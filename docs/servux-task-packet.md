# Servux `litematic_data`：type 2 / type 14 逐字节说明

来源：`sakura-ryoko/servux` tag `1.21.11-0.9.7`（jsDelivr 只读源码）。
包名 `fi.dy.masa.servux.*`，channel id = `Identifier.fromNamespaceAndPath("servux", "litematics")`。

## 1. 包体结构（ServuxLitematicaPacket.fromPacket / toPacket）

wire 布局：`VarInt(packetType)` + 载荷。载荷按类型分两种编码：

```java
case PACKET_C2S_METADATA_REQUEST ->            // type 2
{
    // Read Nbt
    try
    {
//					Optional<BaseData> opt = DataByteBufUtils.fromByteBuf(input);
        return ServuxLitematicaPacket.MetadataRequest(fromVanilla(input.readNbt()));
    }
    ...
}
```

```java
case PACKET_C2S_TASK_REQUEST ->                // type 14
{
    // Read Nbt
    try
    {
        Optional<BaseData> opt = DataByteBufUtils.fromByteBuf(input);

        if (opt.isPresent())
        {
            return ServuxLitematicaPacket.TaskRequest((CompoundData) opt.get());
        }
    }
    catch (Exception e) { Servux.LOGGER.error(
        "ServuxLitematicaPacket#fromPacket: error reading Task Request from packet: [{}]", e.getLocalizedMessage()); }
}
```

发送侧对应：

```java
case PACKET_C2S_METADATA_REQUEST, PACKET_S2C_METADATA -> output.writeNbt(this.toVanilla());
//                    DataByteBufUtils.toByteBuf(output, this.nbt, "");   ← 被注释
// 任务类 5 个类型（含 type 14）：
DataByteBufUtils.toByteBuf(output, this.nbt, "");
```

- type 2 用 **vanilla NBT**：`FriendlyByteBuf.readNbt()` → `fromVanilla(CompoundTag)` → `DataConverterNbt.fromVanillaCompound(nbt)`。
- type 14 用 **自定义编码**：`DataByteBufUtils.fromByteBuf(input)`（**单参数**，不是 `fromByteBuf(input, "")`）。

`toVanilla()` 是 vanilla 侧写入，其内部字节（是否写空根名）**未在 Servux 源码中出现，未找到**；两端用的是同一个 vanilla API，客户端只要同样调用 `FriendlyByteBuf.writeNbt(CompoundTag)` 就必然对称 —— **不要手写 type 2 的字节**。

## 2. `DataByteBufUtils`（type 14 的 load-bearing 布局）

```java
public static Optional<BaseData> fromByteBuf(ByteBuf byteBuf)
{
    final int length = byteBuf.readInt();
    ByteBuf slice = byteBuf.readSlice(length);

    try (ByteBufInputStream bis = new ByteBufInputStream(slice))
    {
        try (GZIPInputStream gis = new GZIPInputStream(bis);
             DataInputStream dis = new DataInputStream(gis))
        {
            return Optional.ofNullable(DataFileUtils.readFromNbtStream(dis, SizeTracker.NETWORK_MAX_BYTES));
        }
    }
    catch (ZipException e)      // 回退：未压缩
    {
        slice.resetReaderIndex();
        try (ByteBufInputStream bis = new ByteBufInputStream(slice);
             DataInputStream dis = new DataInputStream(bis))
        {
            return Optional.ofNullable(DataFileUtils.readFromNbtStream(dis, SizeTracker.NETWORK_MAX_BYTES));
        }
        ...
    }
}
```

```java
public static ByteBuf toByteBuf(@Nonnull ByteBuf byteBuf, @Nullable BaseData data, String rootTagName) throws IOException
{
    if (data == null || data.isEmpty()) { data = EmptyData.INSTANCE; }
    ByteBuf tempBuf = byteBuf.alloc().buffer();
    try
    {
        try (DataOutputStream os = new DataOutputStream(new GZIPOutputStream(new ByteBufOutputStream(tempBuf))))
        {
            if (!DataFileUtils.writeToNbtStream(os, data, rootTagName, SizeTracker.NETWORK_MAX_BYTES)) { ... }
        }
        byteBuf.writeInt(tempBuf.readableBytes());
        byteBuf.writeBytes(tempBuf);
    }
    ...
}
```

- 长度前缀 = **`ByteBuf.writeInt`：4 字节大端，非 VarInt**；GZIP 包体整体位于该长度之内 → `[int32 length][gzip(NBT)]`。
- 读侧 `readInt()` + `readSlice(length)`：**`DataByteBufUtils` 内部没有任何长度上限校验**（不存在“>32767 就拒绝”的逻辑）。32767/32762 是 `network/PacketSplitter.java` 的分片帧上限，只作用于 bulk 分片路径。

流内布局（`util/data/tag/util/DataFileUtils.java`）：

```java
DataOutput dost = new DataOutputSizeTracker(output, new SizeTracker(maxBytes));
dost.writeByte(data.getType());
if (data.getType() != Constants.NBT.TAG_END) { dost.writeUTF(tagName); data.write(dost); }
...
byte tagType = input.readByte();
if (tagType == Constants.NBT.TAG_END) { return null; }
input.readUTF();   // Discard the name of the root tag
return BaseData.createTag(Constants.NBT.TAG_COMPOUND, input, 0, new SizeTracker(maxBytes));
```

→ 解压后 = `0x0A` + `writeUTF("")`（0x00 0x00）+ compound body + 尾部 `0x00`。`rootTagName` 传 `""`，读侧显式丢弃根名。

实际上限：`SizeTracker.NETWORK_MAX_BYTES = 64L*1024L*1024L`（64 MB）；`FILE_MAX_BYTES = 512 MB`；`DEFAULT_MAX_BYTES = 1 GB`，构造时 `Math.min(effectiveMax, DEFAULT_MAX_BYTES)`，超限抛 `SizeTrackerException`。

## 3. `onTaskRequest` 取键与 `Box` codec

```java
final String taskType = tags.getStringOrDefault("Task", "");
...
ListData list = tags.getListOrDefault("Boxes", Constants.NBT.TAG_COMPOUND, new ListData());
List<Box> boxes = new ArrayList<>();
for (int i = 0; i < list.size(); i++)
{
    BaseData entry = list.get(i);
    if (entry != null && !entry.isEmpty())
    {
        Box.CODEC.parse(DataOps.INSTANCE, entry).resultOrPartial().ifPresent(boxes::add);
    }
}
BlockState fillState = tags.getCodec("FillState", BlockState.CODEC).orElse(null);
...
final BlockState replaceState = tags.getCodec("ReplaceState", BlockState.CODEC).orElse(null);
final boolean removeEntities = tags.getBooleanOrDefault("RemoveEntities", false);
final int interval = tags.getIntOrDefault("Interval", 1);
```

“Delete” 分支同样读 `Boxes` / `RemoveEntities` / `Interval`，**不读** `FillState` / `ReplaceState`。

类型语义（`DataView` 默认方法 + `CompoundData`）：

```java
default boolean getBooleanOrDefault(String key, boolean defaultValue) {
    if (this.contains(key, Constants.NBT.TAG_BYTE) == false) { return defaultValue; }
    return this.getBoolean(key); }
default int getIntOrDefault(String key, int defaultValue) {
    if (this.contains(key, Constants.NBT.TAG_ANY_NUMERIC) == false) { return defaultValue; }
    return this.getInt(key); }
default String getStringOrDefault(String key, String defaultValue) {
    if (this.contains(key, Constants.NBT.TAG_STRING) == false) { return defaultValue; }
    return this.getString(key); }
default ListData getListOrDefault(String key, int containedType, ListData defaultValue) {
    ... if (data.getType() != Constants.NBT.TAG_LIST) { return defaultValue; }
    if (list.getContainedType() != containedType) { return defaultValue; } return list; }
```

- `RemoveEntities` 必须是 **TAG_BYTE**（int 会被忽略，退回 false）；`Interval` 接受任意数值类型；`Task` 必须是 TAG_STRING。
- `Boxes` 必须是 `TAG_LIST` 且**列表声明元素类型 == TAG_COMPOUND(10)**；空列表 containedType 是 TAG_END → 同样不匹配 → 回退为空列表。

```java
public static final Codec<Box> CODEC = RecordCodecBuilder.create(
        inst -> inst.group(
                BlockPos.CODEC.fieldOf("pos1").forGetter(get -> get.pos1 != null ? get.pos1 : BlockPos.ZERO),
                BlockPos.CODEC.fieldOf("pos2").forGetter(get -> get.pos2 != null ? get.pos2 : BlockPos.ZERO),
                PrimitiveCodec.STRING.fieldOf("name").forGetter(get -> get.name)
        ).apply(inst, Box::new)
);
private String name = "Unnamed";
```

- 字段名 `pos1` / `pos2` / `name`；**`name` 必填**（无默认值，缺失则该 Box 解析失败被 `resultOrPartial` 静默丢弃 → 表现为 `...task.fill_area.no_boxes`）。
- `pos1`/`pos2` 用 `BlockPos.CODEC`，其 NBT 形状属于 vanilla，Servux 源码不可见。`DataOps`（NbtOps 忠实移植）里 `createIntList → IntArrayData`、`getIntStream` 直接接受 `TAG_INT_ARRAY`，否则回退到 `DynamicOps.super.getIntStream`（list 形式）。→ 编码结果必是“整数序列”形态（`[I;x,y,z]` 或 3 个 IntTag 的 LIST），**不是** `{X,Y,Z}` 复合；解码两种都吃。
- 结论：**不要手写 pos 字节**，在客户端直接 `BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, pos)` 生成 Tag 塞进 Box 复合即可（与 `DataOps` 行为对齐，形状问题自动消失）。

## 4. 症状对照表

| 症状 | 含义 |
| --- | --- |
| 服务端日志 `litematic_data: Denying access for player ... Insufficient Protocol Version` / 客户端收到 `servux.general.error.protocol_version_too_low` | metadata 被解析成空/低版本 → framing 写错，或 `version` < PROTOCOL_VERSION |
| `servux.litematics.task.fill_area.no_boxes` | `Boxes` 缺省、类型不对、元素类型不是 compound，或某个 Box 的 `name` 缺失/pos 形状错 |
| 完全无反应 | 未注册成功（metadata 未通过）或 channel 注册被拒 |

客户端下行参考实现（Litematica 同 tag 的 `network/ServuxLitematicaHandler.java`）：`CHANNEL_ID = Identifier.fromNamespaceAndPath("servux", "litematics")`；收发走 `ClientPlayNetworking` / `IPluginClientPlayHandler`；bulk 分片用 `PacketSplitter.DEFAULT_MAX_RECEIVE_SIZE_S2C - 4096` 作阈值，`DataByteBufUtils.toByteBuf(packet.getCompound(), "")`。
