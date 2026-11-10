@echo off
cd /d "%~dp0"
docker compose -p university-demo -f docker/postgres/pgvector.yml down
pause
