Cloud Picture local demo

Requirements
- Docker Desktop with Docker Compose
- Internet access at runtime for Tencent COS image operations

Start
1. Copy runtime.env.example to .env.
2. Set COS_SECRET_ID, COS_SECRET_KEY and COS_BUCKET if you need image upload/display.
3. Build fresh images from the sibling source directories:
   docker compose -f docker-compose.yml -f docker-compose.build.yml build
   Previously exported tar/zip files contain the old application and must not be reused.
4. Start the application:
   docker compose up -d
5. Open http://localhost:8088
6. Sign in using the administrator credentials from the user-service migrations.

The database starts empty and Flyway creates its schema. Existing database records and COS files are not included.
The demo administrator password is for local viewing only.
Payment, orders and space plans have been removed. Private spaces have no total count/size quota;
the per-file upload checks remain in place. Scheduled AI image review is disabled for this local demo.

For an existing deployment, stop the old payment-service container using the old Compose file
before switching configuration. Do not delete its database or data volumes.

Stop without deleting demo data:
   docker compose down

Do not use `docker compose down -v` unless you want to delete the local database, Redis and Nacos data.
