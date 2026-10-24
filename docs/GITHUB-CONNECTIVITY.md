# GitHub 连接问题：病因、修复与恢复

> 适用：本机（Windows / DESKTOP-LH1HJ59），Watt Toolkit（Steam++）开启 GitHub 加速时。

## 一句话结论

`hosts` 被 Watt Toolkit 改写，把全部 GitHub 域名指向 `127.0.0.1` 交给它的本地 MITM 代理；该代理证书**没有吊销信息**，Windows schannel 会以 `CRYPT_E_NO_REVOCATION_CHECK` 拒绝，于是 `curl` / `Invoke-WebRequest` / 部分工具直接失败。而 Watt 会持续把 `hosts` 抢回去，所以「手工改 hosts」不是可靠解法。

## 判别方法

```powershell
.\tools\github-connect.ps1 -Diagnose
```

看三个信号：

| 现象 | 含义 |
|---|---|
| 解析结果是 `127.0.0.1` 且 Watt 在运行 | 正常，走的是 Watt 代理（客户端兼容模式可覆盖） |
| 解析结果是 `127.0.0.1` 但 Watt **没运行** | 已损坏，所有 GitHub 连接都会超时 → 需要 `-Mode hosts` |
| `curl` 返回 `000` 且报 `CRYPT_E_NO_REVOCATION_CHECK` | 证书吊销检查拦截，客户端兼容模式未生效 |

## 修复（默认，无需管理员）

```powershell
.\tools\github-connect.cmd          # 等价于 -Mode client
```

做三件事，均幂等：

1. `git config --global http.schannelCheckRevoke false` —— 让 git 穿过 Watt 代理
2. `git config --global init.defaultBranch main`
3. 写入 curl 配置 `ssl-no-revoke`。**注意本机设了 `XDG_CONFIG_HOME=D:\opencode\config`，curl 优先读那里的 `curlrc`，单纯的 `_curlrc` 不会被读取**，所以脚本会同时写三处：
   - `%USERPROFILE%\.curlrc`
   - `%USERPROFILE%\_curlrc`
   - `%XDG_CONFIG_HOME%\curlrc`

`gh` CLI 走 Go 自带 TLS 栈、不做吊销检查，只要已登录就能穿过 Watt：

```powershell
gh auth status          # 应显示 Logged in to github.com account dyhdream
gh api user
```

## 彻底方案（推荐，需要一次 UAC）

让 GitHub 完全不走 Watt，这样 Watt 关掉也不影响 GitHub：

1. 打开 Watt Toolkit → **网络加速** → 在加速服务列表里**取消勾选 GitHub**
2. 确认它把 hosts 里的 GitHub 段清掉（或直接退出 Watt）
3. 执行：

```powershell
.\tools\github-connect.cmd -Mode hosts
```

脚本会：备份 hosts → 删除所有指向 `127.0.0.1` 的 GitHub 行和遗留的 `#[gh-fix]` 标记 → 用 DoH（阿里 / DNSPod）解析真实 IP 并写入受管块 → 刷新 DNS → 复检。

> 若 Watt 仍在运行并开着 GitHub 加速，脚本会明确警告：它会在同秒内把改动覆盖掉。

## 恢复

```powershell
.\tools\github-connect.cmd -Restore          # 从最近一次备份还原 hosts
.\tools\github-connect.cmd -Mode watt        # 或：恢复成走 Watt 的 127.0.0.1 形态
```

备份位置：`C:\ProgramData\github-connect\hosts.bak.<时间戳>`

## 实测数据（2026-10-24，Watt 直连 vs 真实 IP 直连）

| 域名 | 真实 IP 直连 | Watt 代理 |
|---|---|---|
| api.github.com | 200 / 0.36s | 200 / 0.75s（需 `ssl-no-revoke`） |
| github.com | 200 / 5.24s | 200 / 5.77s |
| codeload.github.com | 301 / 0.51s | 301 / 1.33s |
| objects.githubusercontent.com | 404 / 0.25s | 404 / 5.96s |
| gist.github.com | 超时 | 302 / 1.03s |
| raw.githubusercontent.com | 301 / 1.30s | 301 / 0.42s（偶发 12s 超时） |

结论：两条路都能用，Watt 的优势有限且不稳定；直连更可预测。

## 备注：还有两个「隐形坑」

- `%USERPROFILE%\.ssh\config` 里只有 gitee 条目；SSH 走 `ssh.github.com:443` 是通的（实测可连），但公钥尚未加到 GitHub 账号，所以 `git@github.com` 目前是 `Permission denied (publickey)`。若想用 SSH 推送，先把 `~/.ssh/id_ed25519.pub` 加到账号，再配 `Host github.com → HostName ssh.github.com / Port 443`。
- GitHub 凭据存于 Git Credential Manager（账号 `dyhdream`，token scope：`gist, read:org, repo`），`gh` 已写入 keyring；换机器或 token 失效时需重新 `gh auth login`。
