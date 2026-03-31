# 将当前 PowerShell 会话切到 UTF-8，降低 Windows 控制台读取源码时出现乱码的概率。
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
chcp 65001 > $null
Write-Host "Current shell encoding switched to UTF-8 (code page 65001)."
