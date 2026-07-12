# QCOFA Offline Skin

> 让你在 QuestCraft 上的离线账号也可以使用皮肤。

## 用途

QCOFA Offline Skin 是一个 Minecraft Fabric 模组，允许玩家在离线/盗版客户端中本地更换皮肤。

**核心特点：**
- ✅ 仅安装本模组的玩家之间可互相看到皮肤
- ✅ 未安装模组的玩家（正版玩家）不可见
- ✅ 不替换原版贴图，使用独立的网络通道同步
- ✅ 适用于 QuestCraft

## 实现原理

### 整体架构

```
客户端                              服务端
  │                                    │
  │  1. 用户在暂停菜单点击「更换皮肤」     │
  │       ↓                            │
  │  2. SkinScreen 打开，选择皮肤 PNG    │
  │       ↓                            │
  │  3. 上传皮肤到服务端 (skin_upload)  ──→  NetworkHandler 接收
  │       ↓                            │       ↓
  │  6. 收到广播 (skin_broadcast) ←─────  4. 存入 ServerSkinStorage
  │       ↓                            │       ↓
  │  7. 注册纹理 + Mixin 覆盖渲染        │  5. 广播给所有模组玩家
  │                                    │
```

### 关键技术点

#### 1. 网络通信

模组使用三个自定义网络通道：

| 通道 | 方向 | 用途 |
|------|------|------|
| `skin_upload` | 客户端 → 服务端 | 上报自己的皮肤信息 |
| `skin_broadcast` | 服务端 → 客户端 | 广播某玩家的皮肤信息 |
| `skin_request` | 服务端 → 客户端 | 请求当前皮肤（玩家加入时） |

**核心设计**：服务端使用 `ServerPlayNetworking.canSend()` 检查客户端是否注册了对应通道的接收器。只有安装了本模组的客户端才会注册这些接收器，因此：
- 未安装模组的玩家不会收到任何广播包
- 正版玩家看到的是默认/正版皮肤

#### 2. 皮肤渲染覆盖

使用 Mixin 注入两个类：

- **`AbstractClientPlayerEntityMixin`**：注入 `getSkinTexture()` / `getSkinTextures()` 方法，检查 `ClientSkinRegistry` 是否有该玩家的离线皮肤，如果有则返回自定义纹理 ID
- **`PlayerListEntryMixin`**：注入玩家列表头像的纹理获取方法，确保 TAB 列表中也显示离线皮肤

#### 3. 纹理注册

客户端收到皮肤广播后，使用 `NativeImage` 解析 PNG 数据，然后通过 `NativeImageBackedTexture` 在运行时注册纹理：

```java
NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
Identifier id = new Identifier(MOD_ID, "skin/" + hash);
client.getTextureManager().registerTexture(id, texture);
ClientSkinRegistry.put(uuid, id, model);
```

#### 4. 本地存储

皮肤文件存储在 `config/qcofa_offline_skin/` 目录：
- `skin.png` - 当前应用的皮肤
- `model.txt` - 当前模型类型（`default` 或 `slim`）

玩家选择新皮肤时，模组将选中的 PNG 复制到 `skin.png`，然后上传到服务端。

### 完整流程

1. **玩家加入服务器**：服务端发送 `skin_request` 通道请求客户端上传皮肤
2. **客户端响应**：读取本地 `skin.png` 和 `model.txt`，通过 `skin_upload` 通道发送到服务端
3. **服务端广播**：服务端将皮肤存入 `ServerSkinStorage`，然后通过 `skin_broadcast` 通道广播给所有安装了模组的玩家
4. **其他客户端渲染**：收到广播后注册纹理，Mixin 注入点替换皮肤纹理，玩家互相看到离线皮肤
5. **皮肤变更**：玩家在游戏中更换皮肤时，重复步骤 2-4
6. **卸下皮肤**：删除本地 `skin.png`，发送长度为 0 的 `skin_upload` 包，服务端广播移除

## 编译指南

### 环境要求

| Minecraft 版本 | Java 版本 | Gradle 版本 | Loom 版本 |
|---------------|----------|-------------|-----------|
| 1.19.2 - 1.20.4 | 17 | 8.8 | 1.6 / 1.7 |
| 1.20.5 - 1.21.5 | 21 | 8.11+ | 1.9 / 1.10 |

### 步骤

#### 1. 克隆仓库

```bash
# 克隆主仓库
git clone https://github.com/XiaoMeow-1145/qcofa-offline-skin.git
cd qcofa-offline-skin

# 切换到对应版本的分支
git checkout 1.20.1-Fabric  # 或其他版本，如 1.21.5-Fabric
```

#### 2. 检查分支

所有支持的版本分支：

```bash
git branch -a
# 输出示例：
#  1.19.2-Fabric
#  1.19.3-Fabric
#  1.19.4-Fabric
#  1.20.1-Fabric
#  1.20.2-Fabric
#  1.20.3-Fabric
#  1.20.4-Fabric
#  1.20.5-Fabric
#  1.20.6-Fabric
#  1.21-Fabric
#  1.21.1-Fabric
#  1.21.3-Fabric
#  1.21.4-Fabric
#  1.21.5-Fabric
```

#### 3. 配置 Java 路径（可选）

编辑 `gradle.properties`，确保 `org.gradle.java.home` 指向正确的 Java 版本：

```properties
# 1.19.2 - 1.20.4 使用 Java 17
org.gradle.java.home=/path/to/java-17

# 1.20.5 - 1.21.5 使用 Java 21
org.gradle.java.home=/path/to/java-21
```

#### 4. 编译

```bash
# 使用项目自带的 Gradle Wrapper
./gradlew build

# 或者使用本地 Gradle（版本需匹配）
gradle build
```

#### 5. 获取产物

编译成功后，jar 文件位于：

```
build/libs/qcofa-offline-skin-1.0.0.jar
```

### 常见问题

#### Gradle 版本不兼容

不同版本的 Fabric Loom 对 Gradle 版本有要求：
- Loom 1.6 / 1.7 → Gradle 8.8
- Loom 1.9 → Gradle 8.11+
- Loom 1.10 → Gradle 8.11+

可以使用 `mise` 管理多版本 Gradle：

```bash
mise install gradle@8.8
mise use gradle@8.8
./gradlew build
```

#### Java 版本错误

确保使用正确的 Java 版本：

```bash
# 检查当前 Java 版本
java -version

# 切换 Java 版本（使用 mise）
mise use java@17
mise use java@21
```

#### 依赖下载慢

可以在 `gradle.properties` 中配置镜像：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=18080
```

## 项目结构

```
qcofa-offline-skin/
├── src/main/java/cn/qcofa/offlineskin/
│   ├── QCOFAOfflineSkin.java          # 主入口（服务端）
│   ├── client/
│   │   ├── QCOFAOfflineSkinClient.java  # 客户端入口
│   │   ├── ClientSkinRegistry.java      # 客户端皮肤注册表
│   │   └── SkinScreen.java              # 皮肤更换界面
│   ├── mixin/
│   │   ├── GameMenuScreenMixin.java         # 暂停菜单按钮注入
│   │   ├── AbstractClientPlayerEntityMixin.java  # 皮肤纹理覆盖
│   │   └── PlayerListEntryMixin.java        # TAB 列表头像覆盖
│   ├── network/
│   │   └── NetworkHandler.java        # 服务端网络处理
│   └── skin/
│       ├── SkinData.java              # 皮肤数据记录
│       ├── ServerSkinStorage.java     # 服务端皮肤存储
│       └── LocalSkinFile.java         # 本地文件读写
├── src/main/resources/
│   ├── fabric.mod.json                # 模组元数据
│   ├── qcofa_offline_skin.mixins.json # Mixin 配置
│   └── assets/qcofa_offline_skin/lang/ # 语言文件
├── build.gradle                       # Gradle 构建配置
├── gradle.properties                  # 版本和环境配置
└── LICENSE                            # MIT 许可证
```

## 依赖

- **Fabric Loader** >= 0.15.0
- **Fabric API**（必须）
- **Minecraft** 1.19.2 - 1.21.5
- **Java** 17 (MC 1.19.x - 1.20.4) 或 Java 21 (MC 1.20.5+)

## 反馈

如遇 bug 请在 [GitHub Issues](https://github.com/XiaoMeow-1145/qcofa-offline-skin/issues) 中反馈。
