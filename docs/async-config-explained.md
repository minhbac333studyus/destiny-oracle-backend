# AsyncConfig — Giải Thích Chi Tiết

## 1. Tổng quan

`AsyncConfig` làm 2 việc chính:
- Tạo **Thread Pool** để chạy các tác vụ nền (`@Async`, `@Scheduled`)
- Đăng ký pool đó vào **Spring MVC** để xử lý SSE streaming

---

## 2. Ba Annotations Trên Class

```java
@Configuration    // Đây là file cấu hình Spring — giống XML config ngày xưa
@EnableAsync      // Bật @Async — method nào có @Async sẽ chạy trên background thread
@EnableScheduling // Bật @Scheduled — method nào có @Scheduled sẽ chạy định kỳ
```

---

## 3. ThreadPoolTaskExecutor — Bộ Máy Quản Lý Thread

```java
executor.setCorePoolSize(4);           // Luôn giữ sẵn 4 thread, kể cả khi rảnh
executor.setMaxPoolSize(8);            // Tối đa 8 thread khi nhiều task đồng thời
executor.setQueueCapacity(100);        // Nếu 8 thread đều bận → xếp hàng, tối đa 100 task
executor.setThreadNamePrefix("destiny-async-"); // Thread tên: destiny-async-1, destiny-async-2...
```

### Flow xử lý khi có task mới:

```
Task mới đến
    │
    ├── Đang có < 4 threads chạy?   → tạo thread mới, chạy ngay
    │
    ├── Đang có = 4 threads chạy?   → cho vào queue chờ (tối đa 100 task)
    │
    ├── Queue đầy (100) + < 8 threads? → tạo thêm thread (tối đa 8)
    │
    └── Queue đầy + đã có 8 threads? → RejectedExecutionException ❌
```

---

## 4. configureAsyncSupport — Dành Cho SSE Streaming

```java
@Override
public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setTaskExecutor(taskExecutor());
}
```

### Không có config này:
```
Client gọi POST /api/v1/chat/stream
    │
    └── Spring MVC dùng SimpleAsyncTaskExecutor
            → Mỗi request tạo 1 thread MỚI (không giới hạn)
            → 1000 user chat đồng thời = 1000 thread = OOM / crash
            → WARN: "not suitable for production"
```

### Có config này:
```
Client gọi POST /api/v1/chat/stream
    │
    └── Spring MVC dùng ThreadPoolTaskExecutor (reuse thread từ pool)
            → Stream Flux<String> chạy trên destiny-async-1
            → Claude gửi chunk → thread forward về client → Claude gửi tiếp...
            → Thread trả về pool sau khi stream xong
```

---

## 5. Toàn Bộ Bức Tranh Khi User Gửi "hi"

```
User gửi "hi"
    │
    ├── [HTTP thread - Tomcat]
    │       AiChatController nhận request
    │       └── Gọi chatService.chat() → trả về Flux<String> (ngay lập tức)
    │
    ├── [destiny-async-1 - Spring MVC SSE]
    │       Claude stream response về client theo từng chunk
    │       ├── chunk 1: "Hello"  → gửi ngay về frontend
    │       ├── chunk 2: "! How"  → gửi ngay về frontend
    │       └── chunk N: "..."    → gửi ngay về frontend
    │               │
    │               └── Khi Claude xong → lưu DB → gọi addToMem0Async()
    │
    └── [destiny-async-2 - @Async background]
            addToMem0Async() chạy ngầm
            └── Gọi Mem0 API lưu memory (không block chat)
```

---

## 6. Câu Hỏi: Mỗi Message Gửi Mem0 1 Lần? Queue Có Bị Nghẽn Không?

### Khi nào Mem0 được gọi?

```java
// AiChatServiceImpl.java — trong onComplete callback (sau khi Claude trả lời xong)
() -> {
    messageRepo.save(assistantMsg);        // Lưu DB trước
    addToMem0Async(userId, message, response); // Sau đó mới gọi Mem0 (background)
}
```

**Có — mỗi lần user gửi message, sau khi Claude trả lời xong → Mem0 được gọi 1 lần.**

### Nếu user gửi liên tục thì sao?

```
User gửi msg 1 → Claude trả lời (5 giây) → Mem0 task 1 vào queue → [destiny-async-2] xử lý
User gửi msg 2 → Claude trả lời (5 giây) → Mem0 task 2 vào queue → [destiny-async-3] xử lý
User gửi msg 3 → Claude trả lời (5 giây) → Mem0 task 3 vào queue → chờ thread rảnh
...
```

### Tại sao KHÔNG bị nghẽn trong thực tế?

| Yếu tố | Giải thích |
|--------|-----------|
| Claude mất 3–10 giây/response | User không thể gửi 100 message/giây được |
| Queue = 100 task | Chứa được 100 Mem0 task đang chờ |
| Core pool = 4 | 4 Mem0 call có thể chạy song song |
| `@Async` không block | Chat vẫn tiếp tục dù Mem0 chậm |
| Mem0 fail → chỉ log DEBUG | Lỗi Mem0 không ảnh hưởng chat |

### Kịch bản xấu nhất:

```
10 user chat cùng lúc
    │
    ├── 10 stream chạy trên 8 threads (2 chờ)
    │
    └── Mỗi conversation xong → 1 Mem0 task
            → 10 Mem0 tasks → 4 chạy song song, 6 vào queue
            → Queue còn trống 94 chỗ → hoàn toàn ổn ✅
```

### Khi nào mới thực sự bị vấn đề?

Chỉ khi **Mem0 cực kỳ chậm** (ví dụ mỗi call mất 30 giây) VÀ **nhiều user cùng chat**:

```
100 user chat đồng thời
→ 100 Mem0 tasks
→ 4 thread xử lý, mỗi task 30 giây
→ Queue đầy 100 → task thứ 101 bị reject ❌
```

Nếu sau này scale lớn, giải pháp là tăng `maxPoolSize` hoặc dùng **message queue** (RabbitMQ/Kafka) thay vì gọi trực tiếp.

---

## 7. Các Task Dùng Thread Pool Này

| Task | Annotation | Tần suất |
|------|-----------|---------|
| `addToMem0Async()` | `@Async` | Mỗi lần chat xong |
| `compressIfNeeded()` | `@Async` | Mỗi lần chat xong (check ngưỡng) |
| `ReminderScheduler.checkDueReminders()` | `@Scheduled` | Mỗi 1 phút |
| `NudgeScheduler.checkAndNudge()` | `@Scheduled` | Mỗi 5 phút |
| `InsightScheduler.generateDailyInsights()` | `@Scheduled` | 23:00 mỗi ngày |
| `StageProgressionScheduler.incrementDaysAtStage()` | `@Scheduled` | 00:00 mỗi ngày |
