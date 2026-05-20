package chatbot;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    // POST /api/chat
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages =
                (List<Map<String, String>>) body.get("messages");

        if (messages == null || messages.isEmpty())
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Tin nhan trong"));

        String reply = chatService.chat(messages);
        return ResponseEntity.ok(Map.of("success", true, "reply", reply));
    }

    // GET /api/sanpham - trả đủ thông tin cho web hiển thị (kèm URL ảnh)
    @GetMapping("/sanpham")
    public ResponseEntity<List<Map<String, Object>>> getSanPham() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SanPham sp : chatService.getSanPham()) {
            String tenDM = "";
            try {
                if (sp.getDanhMuc() != null) {
                    tenDM = sp.getDanhMuc().getTenDM();
                }
            } catch (Exception e) {
                tenDM = "";
            }

            // Dùng MaSP làm key để tránh vấn đề tên file đặc biệt
            String hinhAnh = sp.getHinhAnh() != null ? sp.getHinhAnh() : "";
            String anhUrl  = "";
            if (!hinhAnh.isEmpty()) {
                File f = new File(hinhAnh);
                if (f.exists()) {
                    // URL dạng /api/anh/1, /api/anh/2 - dùng MaSP làm ID
                    anhUrl = "/api/anh/" + sp.getMaSP();
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("maSP",      sp.getMaSP());
            item.put("tenSP",     sp.getTenSP()           != null ? sp.getTenSP()           : "");
            item.put("giaBan",    sp.getGiaBan()           != null ? sp.getGiaBan()           : 0);
            item.put("ton",       sp.getSoLuongTon()       != null ? sp.getSoLuongTon()       : 0);
            item.put("baoHanh",   sp.getThoiGianBaoHanh() != null ? sp.getThoiGianBaoHanh() : 12);
            item.put("danhMuc",   tenDM);
            item.put("trangThai", sp.getTrangThai()        != null ? sp.getTrangThai()        : "");
            item.put("hinhAnh",   hinhAnh);
            item.put("anhUrl",    anhUrl);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/anh/{maSP}
     * Phục vụ file ảnh bằng MaSP — tránh hoàn toàn vấn đề tên file đặc biệt.
     */
    @GetMapping("/anh/{maSP}")
    public ResponseEntity<Resource> serveImage(@PathVariable Integer maSP) {
        try {
            return chatService.getAllSanPham().stream()
                    .filter(sp -> sp.getMaSP().equals(maSP))
                    .filter(sp -> sp.getHinhAnh() != null && !sp.getHinhAnh().isEmpty())
                    .findFirst()
                    .map(sp -> {
                        File f = new File(sp.getHinhAnh());
                        if (!f.exists()) return ResponseEntity.notFound().<Resource>build();
                        try {
                            String contentType = Files.probeContentType(f.toPath());
                            if (contentType == null) contentType = "image/jpeg";
                            return ResponseEntity.ok()
                                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                                    .body((Resource) new FileSystemResource(f));
                        } catch (Exception e) {
                            return ResponseEntity.internalServerError().<Resource>build();
                        }
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // GET /api/khuyenmai - trả danh sách KM đang áp dụng cho web hiển thị
    @GetMapping("/khuyenmai")
    public ResponseEntity<List<Map<String, Object>>> getKhuyenMai() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            for (KhuyenMai km : chatService.getKhuyenMai()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("maKM",             km.getMaKM());
                item.put("tenKM",            km.getTenKM()            != null ? km.getTenKM()            : "");
                item.put("loaiGiam",         km.getLoaiGiam()         != null ? km.getLoaiGiam()         : "");
                item.put("giaTri",           km.getGiaTri()           != null ? km.getGiaTri()           : 0);
                item.put("giamToiDa",        km.getGiamToiDa()        != null ? km.getGiamToiDa()        : 0);
                item.put("donHangToiThieu",  km.getDonHangToiThieu()  != null ? km.getDonHangToiThieu()  : 0);
                item.put("ngayKetThuc",      km.getNgayKetThuc()      != null ? km.getNgayKetThuc().toLocalDate().toString() : "");
                result.add(item);
            }
        } catch (Exception e) {

        }
        return ResponseEntity.ok(result);
    }

    // GET /api/health
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}