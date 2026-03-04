# Mahjong Hex Web Room (Java)

这是一个纯 JDK 的联机网页应用（不依赖 Maven/Gradle）。

## 已实现功能

- 多人同房间联机（电脑、手机都可访问）
- 进入房间必须输入姓名
- 姓名全房间唯一，不允许重复
- 全员共享海克斯池，单轮不重复，抽完自动进入下一轮
- 每个玩家只能看到自己的海克斯，其他人的海克斯内容会隐藏
- 每张海克斯可点击“使用”，弹窗选择“对自己 / 对所有人”
- 使用海克斯时可指定目标玩家（按姓名显示）
- 清空房间会清空玩家、海克斯和记录，所有人需重新加入
- 刷新页面不丢身份和状态（浏览器保存 `playerId` + 服务端持久化）
- 自动同步已优化为“5 秒轮询 + 仅有变更才重绘”，减少页面抖动

## 本地运行

```powershell
cd E:\code\1\mahjong_hex_web
javac -encoding UTF-8 --add-modules jdk.httpserver MahjongHexWebApp.java
java --add-modules jdk.httpserver MahjongHexWebApp
```

默认端口：`8080`  
可通过环境变量覆盖端口：

```powershell
$env:PORT=9000
java --add-modules jdk.httpserver MahjongHexWebApp
```

## 局域网访问（同 Wi-Fi）

- 本机：`http://localhost:8080`
- 手机：`http://你的电脑IPv4:8080`

## 公网联机（不同 Wi-Fi）

### 快速方案（无需部署，直接出公网链接）

项目已提供脚本：

```powershell
cd E:\code\1\mahjong_hex_web
powershell -ExecutionPolicy Bypass -File .\start_public_room.ps1
```

运行后终端会出现类似 `https://xxxx.localhost.run` 的地址。  
把这个地址发给别人，手机和电脑在不同网络下也可进入同一房间。

说明：

- 该方案依赖 `localhost.run` 隧道服务
- 链接通常是临时的，重启脚本后会变化
- 当前终端关闭（或 Ctrl+C）后公网链接失效

如果手机出现 `Failed to fetch`：

1. 确认打开的是 `https://xxxx.localhost.run` 链接，不是 `localhost`
2. 确认房主机器上的脚本终端没有关闭
3. 如果脚本提示端口被占用，先关闭旧 Java 进程再重启脚本

### 稳定方案（长期使用）

把服务部署到公网服务器（例如 Railway / Render / VPS），使用固定域名访问。

关键点：

1. 保持服务监听 `0.0.0.0`（项目已支持）
2. 使用平台分配的 `PORT`（项目已支持）
3. 部署后把公网 URL 发给其他人，手机和电脑都能联机

## 数据文件

- `room_state.bin`：房间状态持久化文件（自动生成）
- `start_public_room.ps1`：启动本地服务并建立临时公网隧道

删除该文件会重置房间状态。

## API

- `GET /api/state?playerId=...`
- `POST /api/join` 参数：`name`, `playerId(可选)`
- `POST /api/draw` 参数：`playerId`
- `POST /api/use` 参数：`playerId`, `cardId`, `target(SELF|ALL)`
- `POST /api/clear`
