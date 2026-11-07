@echo off
chcp 65001 >nul
title 同频 · 房间服务器
cd /d "%~dp0server"

where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo 没有找到 Node.js。
  echo 请先安装 Node.js 18 或更新版本： https://nodejs.org/
  echo.
  pause
  exit /b 1
)

echo.
echo 正在启动同频房间服务器...
echo 手机和电脑连同一个 Wi-Fi 时，在 App 设置里填下面的“局域网”地址。
echo 需要放行 Windows 防火墙时，请允许 Node.js 访问“专用网络”。
echo.
node server.js 8080

echo.
echo 服务器已退出。
pause
