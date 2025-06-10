# Ứng dụng Quản lý Contact Bitrix24

Dự án này là một ứng dụng Spring Boot tích hợp với Bitrix24 REST API để quản lý contact, bao gồm các chức năng xác thực OAuth, quản lý token và các thao tác CRUD cho contact cùng thông tin ngân hàng liên quan. Ứng dụng được thiết kế để đáp ứng yêu cầu của bài kiểm tra được cung cấp.

## Yêu cầu cần thiết

- HƯỚNG DẪN SAU ĐÂY ĐỐI VỚI BITRIX24 ĐANG ĐỂ NGÔN NGỮ LÀ TIẾNG VIỆT!

Để chạy ứng dụng này, cần cài đặt:
- **Java 17** hoặc cao hơn
- **Maven** (để quản lý phụ thuộc và xây dựng dự án)
- **MySQL** (hoặc bất kỳ cơ sở dữ liệu nào tương thích với Spring Data JPA)
- **Ngrok** (để công khai server cục bộ ra internet)
- **Tài khoản Bitrix24** (có quyền tạo contact và cài đặt ứng dụng)
- Ứng dụng Bitrix24 đã đăng ký với client ID và client secret

## Hướng dẫn cài đặt

### 1. Tải mã nguồn từ Repository
Sao chép dự án từ GitHub về máy cục bộ:
```bash
git clone <repository-url>
cd <repository-folder>
```

### 2. Cấu hình Cơ sở dữ liệu
- Tạo một cơ sở dữ liệu MySQL (ví dụ: `bitrix24_db`).
- Cập nhật tệp `application.properties` trong `src/main/resources` với cấu hình cơ sở dữ liệu:
  ```properties
  spring.datasource.url=jdbc:mysql://localhost:3306/bitrix24_db
  spring.datasource.username=<tên-người-dùng>
  spring.datasource.password=<mật-khẩu>
  spring.jpa.hibernate.ddl-auto=update
  ```

### 3. Cấu hình OAuth Bitrix24
- Đăng ký một ứng dụng trong cổng Bitrix24 để lấy `client_id` và `client_secret`.
- Cập nhật tệp `application.properties` với thông tin OAuth Bitrix24:
  ```properties
  bitrix24.client.id=<client-id>
  bitrix24.client.secret=<client-secret>
  bitrix24.oauth.server=https://oauth.bitrix.info
  ```

### 4. Xây dựng và Chạy ứng dụng
- Xây dựng dự án bằng Maven:
  ```bash
  mvn clean install
  ```
- Chạy ứng dụng Spring Boot:
  ```bash
  mvn spring-boot:run
  ```
  Ứng dụng sẽ khởi động tại `http://localhost:8080` (hoặc cổng được chỉ định trong `application.properties`).

### 5. Công khai ứng dụng bằng Ngrok
- Tải và cài đặt Ngrok từ [https://ngrok.com/download](https://ngrok.com/download).
- Khởi động một tunnel Ngrok để công khai server cục bộ:
  ```bash
  ngrok http 8080
  ```
- Ghi lại URL công khai do Ngrok cung cấp (ví dụ: `https://<ngrok-id>.ngrok.io`).
- Cập nhật cài đặt ứng dụng Bitrix24 với URL Ngrok, thêm các endpoint phù hợp (ví dụ: `https://<ngrok-id>.ngrok.io/oauth/install` cho endpoint cài đặt).

### 6. Cài đặt ứng dụng trong Bitrix24
- Trong cổng Bitrix24, vào mục "Tài nguyên cho nhà phát triển", chọn "Khác", chọn "Ứng dụng cục bộ".
- Phần cài đặt chi tiết hiển thị, ở "Đường dẫn xử lý của bạn*" điền URL `https://<ở-đây-là-url-ngrok-cung-cấp>/api/contacts`
- Ở mục "Đường dẫn cài đặt ban đầu" điền URL `https://<ở-đây-là-url-ngrok-cung-cấp>/oauth/install`
- Ở mục "Văn bản mục menu Tiếng Việt (vn) *" điền tên app muốn cài (ví dụ: "test app bitrix24")
- Click Lưu 
- Hai trường "ID Ứng dụng (client_id)" và "Khóa ứng dụng (client_secret)" sẽ hiện ra. Copy các thông tin đó và dán vào "bitrix24.client.id", "bitrix24.client.secret" tương ứng đã viết ở file `application.properties`.
- Click "Cài đặt lại" -> ứng dụng được cài đặt, và sẽ sang giao diện của ứng dụng. Ở đây sẽ thao tác thêm sửa xóa các contact.
- Sau khi thao tác với app, có thể check lại ở giao diện chính của BITRIX24, không thông qua giao diện app bằng cách: Click chọn "CRM" ở thanh trái, ở thanh trên, chọn "Khách hàng", chọn "Các liên lạc" sẽ thấy các liên hệ được tạo.

### 7. Kiểm tra ứng dụng
- Truy cập vào ứng dụng vừa mới cài đặt
- Sử dụng giao diện được cung cấp để thêm, sửa hoặc xóa contact, bao gồm thông tin cơ bản (tên, số điện thoại, email, website, địa chỉ) và thông tin ngân hàng (tên ngân hàng, số tài khoản).
- Kiểm tra các endpoint API bằng công cụ như Postman:
  - GET `/api/contacts` - Liệt kê tất cả contact
  - GET `/api/contacts/{id}` - Lấy thông tin contact cụ thể
  - POST `/api/contacts` - Tạo contact mới
  - PUT `/api/contacts/{id}` - Cập nhật contact
  - DELETE `/api/contacts/{id}` - Xóa contact
  - GET `/api/requisites` - Liệt kê requisites của contact
  - GET `/api/requisites/bankdetails` - Liệt kê thông tin ngân hàng

### 8. Các tính năng chính
- **Xác thực OAuth**:
  - Xử lý sự kiện cài đặt (`/oauth/install`) và lưu token vào cơ sở dữ liệu.
  - Tự động làm mới token khi hết hạn (kiểm tra trước 5 phút).
  - Hỗ trợ cả chế độ giao diện và script-only cho OAuth Bitrix24.
- **Quản lý Contact**:
  - Các thao tác CRUD cho contact qua REST API (`/api/contacts`).
  - Hỗ trợ thông tin ngân hàng bổ sung được lưu dưới dạng requisites trong Bitrix24.
- **Xử lý lỗi**:
  - Xử lý các lỗi phổ biến như token hết hạn, vấn đề mạng và lỗi API.
  - Ghi log chi tiết các thông báo lỗi để gỡ lỗi.
- **Giao diện người dùng**:
  - Giao diện HTML đơn giản (`app.html`) để quản lý contact với các trường cho tên, số điện thoại, email, website, địa chỉ, tên ngân hàng và số tài khoản.
  - Sử dụng JavaScript để tương tác động với API backend.

### 9. Lưu ý
- Đảm bảo tài khoản Bitrix24 có quyền tạo và quản lý contact cũng như requisites.
- Giao diện (`app.html`) sử dụng các thư viện được lưu trữ trên CDN (Bootstrap, jQuery, DataTables). Đảm bảo kết nối internet để tải các tài nguyên này.

### 10. Khắc phục sự cố
- **Lỗi Token**: Kiểm tra log để tìm vấn đề về token hết hạn hoặc không hợp lệ. Đảm bảo `client_id` và `client_secret` chính xác.
- **Lỗi API**: Xác minh các endpoint REST API Bitrix24 và quyền trong tài khoản Bitrix24.
- **Vấn đề Ngrok**: Đảm bảo tunnel Ngrok đang hoạt động và URL được thiết lập đúng trong Bitrix24.

## Cấu trúc Dự án
- **Controllers**:
  - `AppController.java`: Xử lý trang chính của ứng dụng.
  - `ContactController.java`: Quản lý các endpoint API liên quan đến contact.
  - `OAuthController.java`: Xử lý các endpoint liên quan đến OAuth.
- **Services**:
  - `BitrixApiService.java`: Xử lý gọi API chung.
  - `ContactService.java`: Logic nghiệp vụ cho các thao tác contact và requisite.
  - `TokenService.java`: Quản lý và làm mới token.
- **Entity/Repository**:
  - `TokenEntity.java`: Entity JPA để lưu trữ token.
  - `TokenRepository.java`: Repository JPA cho các thao tác với token.
- **Cấu hình**:
  - `WebConfig.java`: Cấu hình xử lý tài nguyên tĩnh.
- **Giao diện**:
  - `app.html`: Giao diện chính để quản lý contact.
