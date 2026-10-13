# English Center - Docker Setup

## Cấu trúc Docker

Dự án sử dụng Docker Compose với 2 services:
- **PostgreSQL**: Database server
- **Spring Boot App**: Ứng dụng Java

## Yêu cầu

- Docker Desktop hoặc Docker Engine
- Docker Compose

## Cách sử dụng

### 0. Tạo file `.env` (bắt buộc)

```bash
cp .env.example .env
```

Mở `.env` và điền mật khẩu DB, `JWT_SIGNER_KEY` (>= 64 ký tự). File `.env` đã nằm trong `.gitignore`, **không commit**.

### 1. Khởi động toàn bộ ứng dụng

```bash
docker-compose up -d
```

Lệnh này sẽ:
- Build Docker image cho Spring Boot application
- Khởi động PostgreSQL database
- Khởi động Spring Boot application
- Tự động kết nối database

### 2. Xem logs

```bash
# Xem logs của tất cả services
docker-compose logs -f

# Xem logs của app
docker-compose logs -f app

# Xem logs của database
docker-compose logs -f postgres
```

### 3. Dừng ứng dụng

```bash
docker-compose down
```

### 4. Dừng và xóa dữ liệu

```bash
docker-compose down -v
```

### 5. Rebuild application

Sau khi thay đổi code:

```bash
docker-compose up -d --build
```

## Truy cập ứng dụng

- **Application**: http://localhost:8080
- **PostgreSQL**: localhost:5432
  - Database / Username / Password: theo `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` trong `.env`

## Cấu hình môi trường

Toàn bộ thông tin nhạy cảm (DB, JWT key) nằm trong file `.env`.
`application.properties` và `compose.yaml` chỉ tham chiếu biến, ví dụ `${DB_PASSWORD}`.

## Development

### Chạy local (không dùng Docker)

1. Khởi động chỉ database:
```bash
docker-compose up -d postgres
```

2. Chạy Spring Boot từ IDE hoặc Maven:
```bash
./mvnw spring-boot:run
```

### Kết nối database từ local

Application sẽ tự động kết nối đến database ở `localhost:5432`

## Troubleshooting

### Application không kết nối được database

Kiểm tra database đã chạy chưa:
```bash
docker-compose ps
```

### Port đã được sử dụng

Nếu port 8080 hoặc 5432 đã được sử dụng, thay đổi trong `compose.yaml`:
```yaml
ports:
  - '8081:8080'  # Thay đổi port bên ngoài
```

### Xem trạng thái containers

```bash
docker-compose ps
```

### Vào bên trong container

```bash
# Vào app container
docker exec -it englishcenter-app bash

# Vào postgres container
docker exec -it englishcenter-db psql -U <DB_USERNAME> -d <DB_NAME>
```
