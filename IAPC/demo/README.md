# Лабораторный проект: Spring AI и Ollama

Приложение работает локально через Ollama. Ключ OpenAI не нужен.
Используются Java 25, Spring Boot 4.0.8, Spring AI 2.0.1,
`gemma3:1b` для ответов и `nomic-embed-text` для эмбеддингов.

## Запуск на этом компьютере

Открой папку с этим README и запусти `start.cmd` двойным щелчком.
Скрипт проверит Ollama и Docker Desktop, запустит учебную базу,
при необходимости загрузит модели и запустит Spring Boot на порту 8081.
Первый запуск требует интернет для зависимостей, моделей и образа PostgreSQL.
Дождись сообщения `Started DemoApplication` и завершения начальной индексации.
При следующих запусках уже загруженные модели используются повторно.

Из терминала PowerShell:

```powershell
Set-Location -LiteralPath 'G:\Java 25 (for masterat)\demo\demo'
.\start.cmd
```

Для остановки приложения нажми Ctrl+C в его терминале.
`stop-db.cmd` останавливает учебную базу, сохраняя данные в Docker volume.
Ollama и Docker Desktop можно закрыть через их обычные интерфейсы.

Это REST API, как в исходном примере: веб-страницы с чатом нет.
Открытие корневого адреса в браузере не отправляет вопрос. Используй POST-запросы ниже,
Postman или файл `requests.http`.

## Возможности и запросы

Все запросы отправляются с `Content-Type: text/plain; charset=utf-8`.
Тело запроса — обычный текст, ответ — текст модели. Ограничения одним предложением нет.

| Адрес POST | Назначение |
| --- | --- |
| `/api/ai/chat/ask` | Обычный чат — сохранён исходный адрес твоего проекта |
| `/api/ollama/chat/summarize` | Обычный чат — адрес из проекта подруги |
| `/api/employee-handbook/ask` | Вопрос по справочнику сотрудников из Markdown |
| `/api/documents/ask` | Вопрос по PDF, проиндексированным в PostgreSQL |

Пример в PowerShell, когда приложение запущено в другом окне:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8081/api/ai/chat/ask' -Method Post -ContentType 'text/plain; charset=utf-8' -Body 'Say hello in one short sentence.'

Invoke-RestMethod -Uri 'http://localhost:8081/api/employee-handbook/ask' -Method Post -ContentType 'text/plain; charset=utf-8' -Body 'How many paid vacation days do full-time employees receive per year?'

Invoke-RestMethod -Uri 'http://localhost:8081/api/documents/ask' -Method Post -ContentType 'text/plain; charset=utf-8' -Body 'Summarize the policy in the documents.'
```

В справочнике указано 20 дней оплачиваемого отпуска в год.
Материалы на английском языке — те же учебные документы, что в примере подруги.
Можно задавать вопросы по-русски; качество и язык ответа зависят от небольшой локальной модели.
Если в PDF-хранилище нет подходящих материалов, PDF-чат сообщает об отсутствии контекста.

## Работа с документами

Markdown-справочник находится в `src/main/resources/rag/mini-employee-handbook/files`:
HR, IT, офис, безопасность и командировки.
При запуске документы делятся на фрагменты и индексируются в `data/vector-store/vectorstore.json`.
Индекс автоматически перестраивается при изменении документов, параметров разбиения или embedding-модели.
После правок Markdown перезапусти приложение.

PDF клади в `data/pdfs` (папка создаётся автоматически).
При запуске приложение проверяет PDF, а затем следит за созданием, изменением и удалением файлов.
Одинаковый файл повторно не индексируется: сравнивается SHA-256.
При изменении старые фрагменты заменяются, при удалении — удаляются из базы.
Для отсканированных PDF нужен текстовый слой: OCR в проект не входит.
Режим `page` работает с обычными PDF; `paragraph` требует подходящей структуры оглавления PDF.

Конфигурация — `src/main/resources/application.yaml`:

- `app.rag.top-k`: число найденных фрагментов;
- `app.rag.similarity-threshold`: минимальное сходство;
- `app.rag.force-rebuild`: принудительная перестройка при запуске, обычно `false`;
- `app.rag.pdf.path`: шаблон файлов PDF;
- `app.rag.pdf.watch-enabled`: слежение за папкой;
- `spring.ai.ollama.chat.options.model`: модель ответа;
- `spring.ai.ollama.embedding.options.model`: embedding-модель.

При изменении embedding-модели проверь размерность векторов PostgreSQL:
`nomic-embed-text` использует 768. Старые векторы другой модели несовместимы с новыми.

## База данных

Docker Compose: `docker/postgres/pgvector.yml`.
База доступна только локально: `localhost:5433`, БД `postgres`,
пользователь `postgres`, пароль `postgres` (учебная локальная конфигурация).
Схема и расширение vector создаются автоматически.

Таблица `rag_vector_store` хранит фрагменты PDF и их метаданные.
Таблица `vector_store` подготовлена для векторной памяти, как в примере подруги;
сохранение истории диалогов в контроллерах не подключено.

```powershell
docker compose -p university-demo -f docker/postgres/pgvector.yml up -d --wait
docker compose -p university-demo -f docker/postgres/pgvector.yml ps
```

Подключение можно переопределить переменными окружения `DB_URL`, `DB_USER`, `DB_PASSWORD`;
адрес Ollama — `OLLAMA_BASE_URL`. Скрипт start.cmd рассчитан на локальные сервисы по умолчанию.

## Сборка и тесты

Сначала запусти Ollama с обеими моделями и базу данных.
Останови запущенный экземпляр приложения перед интеграционными тестами:
тесты используют ту же папку PDF и базу.

```powershell
.\mvnw.cmd -B -ntp clean verify
java -jar .\target\demo-0.0.1-SNAPSHOT.jar
```

Тесты проверяют запуск Spring, наличие расширения pgvector и полный цикл PDF:
создание → индексация → повтор без дублей → изменение → удаление.
Тестовый PDF создаётся автоматически и удаляется после проверки.

## Основные классы

- `config/AIProviderConfig`: обычный и RAG ChatClient, два pgvector-хранилища и фоновые исполнители.
- `rag/config/SimpleRagConfiguration`: Markdown, разбиение на фрагменты и файловый векторный индекс.
- `rag/config/RagStartupConfiguration`: запуск первичной индексации PDF.
- `rag/config/service/RagIngestionService`: чтение PDF, checksum, метаданные и обновление PostgreSQL.
- `rag/config/service/PdfWatcherService`: отслеживание изменений PDF с задержкой объединения событий.
- `rag/config/controller/EmployeeHandbookController`: ответы по Markdown и демонстрация cosine similarity.
- `rag/config/controller/DocumentChatController`: ответы по PDF через RetrievalAugmentationAdvisor.

QuestionAnswerAdvisor ищет фрагменты справочника в SimpleVectorStore.
RetrievalAugmentationAdvisor ищет PDF-фрагменты в pgvector.
Найденный контекст передаётся в Ollama вместе с вопросом.

## Если не запускается

- Ошибка соединения с `11434`: запусти Ollama и проверь наличие моделей.
- Ошибка соединения с `5433`: проверь Docker Desktop и состояние контейнера.
- Порт `8081` занят: останови предыдущий экземпляр приложения.
- Ошибка `release version 25 not supported`: выбери JDK 25 в IDE и проверь JAVA_HOME.
- После переноса/удаления Java-классов выполняй `mvnw.cmd clean verify`, чтобы убрать устаревшие class-файлы.

Первый ответ Ollama на этом компьютере может занять несколько минут из-за прогрева модели/GPU.
Для HTTP-клиента установи тайм-аут не меньше 360 секунд на первый запрос.
После прогрева короткие ответы формируются значительно быстрее.

Официальная документация: [Spring AI RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html),
[pgvector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html).
