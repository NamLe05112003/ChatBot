package chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    @Value("${groq.api.key}")   private String apiKey;
    @Value("${groq.api.url}")   private String apiUrl;
    @Value("${groq.api.model}") private String model;

    private final SanPhamRepository       sanPhamRepo;
    private final KhachHangRepository     khachHangRepo;
    private final KhuyenMaiRepository     khuyenMaiRepo;
    private final HoaDonRepository        hoaDonRepo;
    private final ChiTietHoaDonRepository chiTietRepo;
    private final WebClient.Builder       webClientBuilder;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String TRANG_THAI_DANG_BAN   = "\u0110ang b\u00e1n";
    private static final String TRANG_THAI_DANG_XU_LY = "\u0110ang x\u1eed l\u00fd";

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Pattern lệnh ORDER – tạo đơn mới
    private static final Pattern ORDER_PATTERN =
            Pattern.compile("<<<ORDER:\\s*(\\{[^}]*\\})\\s*>>>", Pattern.DOTALL);

    // Pattern lệnh UPDATE_PTTT – chỉ đổi phương thức thanh toán
    private static final Pattern UPDATE_PTTT_PATTERN =
            Pattern.compile("<<<UPDATE_PTTT:\\s*(\\{[^}]*\\})\\s*>>>", Pattern.DOTALL);

    // ── [MỚI] Pattern lệnh LOOKUP_ORDER – tra cứu lịch sử mua hàng
    private static final Pattern LOOKUP_ORDER_PATTERN =
            Pattern.compile("<<<LOOKUP_ORDER:\\s*(\\{[^}]*\\})\\s*>>>", Pattern.DOTALL);

    // ═══════════════════════════════════════════════════════════════
    //  GỌI AI
    // ═══════════════════════════════════════════════════════════════
    public String chat(List<Map<String, String>> messages) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model",       model);
            body.put("max_tokens",  700);
            body.put("temperature", 0.7);

            ArrayNode msgs = body.putArray("messages");
            msgs.addObject().put("role", "system").put("content", buildPrompt());
            for (Map<String, String> m : messages) {
                msgs.addObject()
                        .put("role",    m.get("role"))
                        .put("content", m.get("content"));
            }

            String response = webClientBuilder.build()
                    .post().uri(apiUrl)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .bodyValue(mapper.writeValueAsString(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String aiReply = mapper.readTree(response)
                    .path("choices").get(0)
                    .path("message").path("content").asText();

            log.info("AI reply: {}", aiReply);

            // Ưu tiên xử lý theo thứ tự
            if (UPDATE_PTTT_PATTERN.matcher(aiReply).find()) {
                return processUpdatePTTT(aiReply);
            }
            if (ORDER_PATTERN.matcher(aiReply).find()) {
                return processOrder(aiReply);
            }
            // ── [MỚI] Xử lý tra cứu lịch sử
            if (LOOKUP_ORDER_PATTERN.matcher(aiReply).find()) {
                return processLookupOrder(aiReply);
            }
            return aiReply;

        } catch (Exception e) {
            log.error("Loi Groq: {}", e.getMessage());
            return "Xin loi, toi dang gap su co. Vui long thu lai!";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  XÂY DỰNG SYSTEM PROMPT
    // ═══════════════════════════════════════════════════════════════
    private String buildPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ban la tro ly AI cua cua hang gia dung Viet Nam. ")
                .append("Tu van bang tieng Viet, than thien, ngan gon (toi da 120 tu).\n\n");

        sb.append("SAN PHAM DANG BAN:\n");
        try {
            List<SanPham> dsSP = sanPhamRepo.findDangBan();
            if (dsSP.isEmpty()) {
                sb.append("- Chua co san pham\n");
            } else {
                for (SanPham s : dsSP) {
                    sb.append("- ").append(s.getTenSP())
                            .append(": ").append(String.format("%,.0f", s.getGiaBan())).append("d")
                            .append(" | BH ").append(s.getThoiGianBaoHanh()).append(" thang")
                            .append(" | Con ").append(s.getSoLuongTon()).append(" cai\n");
                }
            }
        } catch (Exception e) {
            sb.append("- Khong doc duoc du lieu\n");
        }

        sb.append("\nKHUYEN MAI DANG AP DUNG:\n");
        try {
            List<KhuyenMai> kms = khuyenMaiRepo.findByTrangThai("Đang áp dụng");
            if (kms.isEmpty()) {
                sb.append("- Khong co khuyen mai\n");
            } else {
                for (KhuyenMai km : kms) {
                    sb.append("- Ma: ").append(km.getTenKM());
                    if ("Phần trăm".equals(km.getLoaiGiam())) {
                        sb.append(" | Giam ").append(String.format("%.0f", km.getGiaTri())).append("%");
                        if (km.getGiamToiDa() != null && km.getGiamToiDa().compareTo(java.math.BigDecimal.ZERO) > 0)
                            sb.append(" (toi da ").append(String.format("%,.0f", km.getGiamToiDa())).append("d)");
                    } else {
                        sb.append(" | Giam ").append(String.format("%,.0f", km.getGiaTri())).append("d");
                    }
                    if (km.getDonHangToiThieu() != null && km.getDonHangToiThieu().compareTo(java.math.BigDecimal.ZERO) > 0)
                        sb.append(" | Don tu ").append(String.format("%,.0f", km.getDonHangToiThieu())).append("d");
                    sb.append(" | Het han: ").append(km.getNgayKetThuc() != null ? km.getNgayKetThuc().toString() : "?");
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("- Khong doc duoc du lieu\n");
        }

        sb.append("\nCHINH SACH:\n")
                .append("- Giao noi thanh: 2-4 gio | Ngoai thanh: 1-2 ngay\n")
                .append("- Mien phi ship don tu 500.000d\n")
                .append("- Tich diem: moi 10.000d = 1 diem | 100 diem = 10.000d\n")
                .append("- Thanh toan: Tien mat / Chuyen khoan / The / Vi dien tu\n\n")

                .append("QUY TRINH DAT HANG:\n")
                .append("Buoc 1: Hoi Ten khach, So dien thoai, Dia chi giao hang.\n")
                .append("NEU khach noi da dat hang truoc hoac co thong tin cu: chi can xac nhan lai SDT la du, he thong tu tim thong tin.\n")
                .append("Buoc 2: Hoi phuong thuc thanh toan (Tien mat / Chuyen khoan / The / Vi dien tu).\n")
                .append("Buoc 3: Xac nhan lai thong tin roi xuat lenh tao don:\n")
                .append("<<<ORDER:{\"tenKH\":\"...\",\"soDT\":\"...\",\"diaChi\":\"...\",\"sanPham\":\"...\",\"soLuong\":1,\"pttt\":\"Tien mat\"}>>>\n\n")

                .append("QUAN TRONG - KHI KHACH DOI PHUONG THUC THANH TOAN (sau khi da co don hang):\n")
                .append("NEU khach noi doi/thay phuong thuc thanh toan, chi xuat lenh UPDATE, KHONG tao don moi:\n")
                .append("<<<UPDATE_PTTT:{\"maHD\":SO_HD,\"pttt\":\"Chuyen khoan\"}>>>\n\n")

                // ── [MỚI] Hướng dẫn tra cứu lịch sử
                .append("TRA CUU LICH SU MUA HANG:\n")
                .append("Khi khach hoi 'toi da mua gi', 'lich su don hang', 'don hang cua toi', 'mua gi ngay ...' hoac bat ky cau hoi lien quan den don hang cu:\n")
                .append("Buoc 1: Hoi so dien thoai de xac nhan danh tinh (neu chua co trong cuoc tro chuyen).\n")
                .append("Buoc 2: Neu khach da cung cap SDT, NGAY xuat lenh tim kiem (KHONG hoi them):\n")
                .append("<<<LOOKUP_ORDER:{\"soDT\":\"0xxxxxxxxx\"}>>>\n")
                .append("Neu khach muon loc theo ngay cu the: <<<LOOKUP_ORDER:{\"soDT\":\"0xxxxxxxxx\",\"ngay\":\"DD/MM/YYYY\"}>>>\n")
                .append("Neu khach muon loc theo thang: <<<LOOKUP_ORDER:{\"soDT\":\"0xxxxxxxxx\",\"thang\":\"MM/YYYY\"}>>>\n")
                .append("Ket qua se tu dong tra ve, KHONG tu them thong tin don hang, chi doc ket qua tra ve va tom tat ngan gon.\n\n")

                .append("KHONG hoi email. JSON phai o 1 dong. Dung emoji.");

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════
    //  [MỚI] TRA CỨU LỊCH SỬ ĐƠN HÀNG THEO SĐT
    // ═══════════════════════════════════════════════════════════════
    private String processLookupOrder(String aiReply) {
        Matcher m = LOOKUP_ORDER_PATTERN.matcher(aiReply);
        if (!m.find()) return aiReply;

        String clean   = aiReply.replaceAll("<<<LOOKUP_ORDER:\\s*\\{[^}]*\\}\\s*>>>", "").trim();
        String jsonStr = m.group(1).trim();
        log.info("Tra cuu lich su: {}", jsonStr);

        try {
            JsonNode n    = mapper.readTree(jsonStr);
            String soDT   = n.path("soDT").asText("").trim();
            String ngay   = n.path("ngay").asText("").trim();   // DD/MM/YYYY (tuỳ chọn)
            String thang  = n.path("thang").asText("").trim();  // MM/YYYY    (tuỳ chọn)

            if (soDT.isEmpty()) {
                return clean + "\n\nVui long cung cap so dien thoai de tra cuu don hang!";
            }

            // 1. Tìm khách hàng
            KhachHang kh = khachHangRepo.findBySoDienThoai(soDT).orElse(null);
            if (kh == null) {
                return clean + "\n\nKhong tim thay khach hang voi SDT: " + soDT
                        + "\nVui long kiem tra lai so dien thoai!";
            }

            // 2. Lấy danh sách hóa đơn
            List<HoaDon> dsHD = hoaDonRepo.findByMaKHOrderByNgayLapDesc(kh.getMaKH());

            if (dsHD.isEmpty()) {
                return clean + "\n\nKhach hang " + kh.getTenKH()
                        + " chua co don hang nao trong he thong.";
            }

            // 3. Lọc theo ngày / tháng nếu có yêu cầu
            DateTimeFormatter filterFmtNgay  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter filterFmtThang = DateTimeFormatter.ofPattern("MM/yyyy");

            final String ngayFilter  = ngay;
            final String thangFilter = thang;

            if (!ngayFilter.isEmpty()) {
                try {
                    java.time.LocalDate filterDate =
                            java.time.LocalDate.parse(ngayFilter, filterFmtNgay);
                    dsHD = dsHD.stream()
                            .filter(hd -> hd.getNgayLap() != null
                                    && hd.getNgayLap().toLocalDate().equals(filterDate))
                            .collect(java.util.stream.Collectors.toList());
                } catch (Exception ignored) {}
            } else if (!thangFilter.isEmpty()) {
                try {
                    String[] parts = thangFilter.split("/");
                    int mm = Integer.parseInt(parts[0]);
                    int yy = Integer.parseInt(parts[1]);
                    dsHD = dsHD.stream()
                            .filter(hd -> hd.getNgayLap() != null
                                    && hd.getNgayLap().getMonthValue() == mm
                                    && hd.getNgayLap().getYear() == yy)
                            .collect(java.util.stream.Collectors.toList());
                } catch (Exception ignored) {}
            }

            if (dsHD.isEmpty()) {
                String filter = !ngayFilter.isEmpty() ? "ngay " + ngayFilter
                        : (!thangFilter.isEmpty() ? "thang " + thangFilter : "");
                return clean + "\n\nKhong tim thay don hang nao"
                        + (filter.isEmpty() ? "" : " trong " + filter) + ".";
            }

            // 4. Giới hạn tối đa 10 đơn gần nhất để không tràn context
            int limit = Math.min(dsHD.size(), 10);
            List<HoaDon> dsHienThi = dsHD.subList(0, limit);

            // 5. Xây chuỗi kết quả
            StringBuilder result = new StringBuilder();
            result.append("LICH SU MUA HANG - Khach hang: ").append(kh.getTenKH())
                    .append(" | SDT: ").append(soDT)
                    .append(" | Diem tich luy: ").append(kh.getDiemTichLuy()).append(" diem\n");

            if (!ngayFilter.isEmpty())
                result.append("Loc theo ngay: ").append(ngayFilter).append("\n");
            else if (!thangFilter.isEmpty())
                result.append("Loc theo thang: ").append(thangFilter).append("\n");

            result.append("─".repeat(40)).append("\n");

            for (HoaDon hd : dsHienThi) {
                String ngayLap = hd.getNgayLap() != null
                        ? hd.getNgayLap().format(DATE_FMT) : "?";

                result.append("Don #").append(hd.getMaHD())
                        .append(" | ").append(ngayLap)
                        .append(" | ").append(trangThaiShort(hd.getTrangThai()))
                        .append("\n");

                // Chi tiết sản phẩm trong đơn
                try {
                    List<ChiTietHoaDon> dsCT = chiTietRepo.findByMaHD(hd.getMaHD());
                    for (ChiTietHoaDon ct : dsCT) {
                        String tenSP = "SP#" + ct.getMaSP();
                        // Nếu entity ChiTietHoaDon có trường tenSP thì dùng luôn
                        try {
                            SanPham sp = sanPhamRepo.findById(ct.getMaSP()).orElse(null);
                            if (sp != null) tenSP = sp.getTenSP();
                        } catch (Exception ignored) {}

                        result.append("  + ").append(tenSP)
                                .append(" x").append(ct.getSoLuong())
                                .append(" = ").append(String.format("%,.0f", ct.getThanhTien())).append("d\n");
                    }
                } catch (Exception e) {
                    result.append("  (Khong lay duoc chi tiet)\n");
                }

                result.append("  Tong: ").append(String.format("%,.0f", hd.getTongThanhToan())).append("d")
                        .append(" | TT: ").append(hd.getPhuongThucTT() != null ? hd.getPhuongThucTT() : "?")
                        .append("\n");
                result.append("─".repeat(40)).append("\n");
            }

            if (dsHD.size() > limit) {
                result.append("(Hien thi ").append(limit).append("/").append(dsHD.size())
                        .append(" don gan nhat)\n");
            }

            log.info("Tra cuu KH #{} - {} don hang", kh.getMaKH(), dsHienThi.size());
            return clean + "\n\n" + result;

        } catch (Exception e) {
            log.error("Loi tra cuu don hang: {}", e.getMessage(), e);
            return clean + "\n\nCo loi xay ra khi tra cuu. Vui long thu lai!";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  TẠO ĐƠN HÀNG MỚI
    // ═══════════════════════════════════════════════════════════════
    @Transactional
    private String processOrder(String aiReply) {
        Matcher m = ORDER_PATTERN.matcher(aiReply);
        if (!m.find()) return aiReply;

        String clean  = aiReply.replaceAll("<<<ORDER:\\s*\\{[^}]*\\}\\s*>>>", "").trim();
        String jsonStr = m.group(1).trim();
        log.info("Tao don moi: {}", jsonStr);

        try {
            JsonNode n    = mapper.readTree(jsonStr);
            String tenKH  = n.path("tenKH").asText("").trim();
            String soDT   = n.path("soDT").asText("").trim();
            String diaChi = n.path("diaChi").asText("").trim();
            String tenSP  = n.path("sanPham").asText("").trim();
            int    soLg   = n.path("soLuong").asInt(1);
            String pttt   = n.path("pttt").asText("Tien mat").trim();

            if (tenKH.isEmpty() || soDT.isEmpty() || diaChi.isEmpty() || tenSP.isEmpty()) {
                return clean + "\n\nVui long cung cap du thong tin: Ten, SDT, Dia chi!";
            }

            List<SanPham> spList = sanPhamRepo.findByTenSPContainingIgnoreCase(tenSP);
            if (spList.isEmpty()) return clean + "\n\nKhong tim thay san pham: " + tenSP;

            SanPham sp = spList.get(0);
            if (sp.getSoLuongTon() < soLg)
                return clean + "\n\nChi con " + sp.getSoLuongTon() + " san pham!";

            KhachHang kh = khachHangRepo.findBySoDienThoai(soDT).map(existing -> {
                log.info("Khach hang da ton tai: {} - {}", existing.getTenKH(), soDT);
                if (diaChi != null && !diaChi.isEmpty() && !diaChi.equals(existing.getDiaChi())) {
                    existing.setDiaChi(diaChi);
                    khachHangRepo.save(existing);
                }
                return existing;
            }).orElseGet(() -> {
                KhachHang k = new KhachHang();
                k.setTenKH(tenKH);
                k.setSoDienThoai(soDT);
                k.setDiaChi(diaChi);
                k.setNgayDangKy(LocalDateTime.now());
                k.setDiemTichLuy(0);
                log.info("Tao KH moi: {} - {}", tenKH, soDT);
                return khachHangRepo.save(k);
            });

            BigDecimal tongTien = sp.getGiaBan().multiply(BigDecimal.valueOf(soLg));

            HoaDon hd = new HoaDon();
            hd.setMaKH(kh.getMaKH()); hd.setMaNV(1);
            hd.setNgayLap(LocalDateTime.now());
            hd.setTongTienGoc(tongTien); hd.setSoTienGiam(BigDecimal.ZERO);
            hd.setDiemSuDung(0); hd.setSoTienTuDiem(BigDecimal.ZERO);
            hd.setTongThanhToan(tongTien); hd.setPhuongThucTT(pttt);
            hd.setTrangThai(TRANG_THAI_DANG_XU_LY);
            hd = hoaDonRepo.save(hd);
            log.info("Da luu HoaDon #{}", hd.getMaHD());

            ChiTietHoaDon ct = new ChiTietHoaDon();
            ct.setMaHD(hd.getMaHD()); ct.setMaSP(sp.getMaSP());
            ct.setSoLuong(soLg); ct.setDonGia(sp.getGiaBan()); ct.setThanhTien(tongTien);
            chiTietRepo.save(ct);

            sp.setSoLuongTon(sp.getSoLuongTon() - soLg);
            sanPhamRepo.save(sp);

            int diemCong = tongTien.intValue() / 10000;
            kh.setDiemTichLuy(kh.getDiemTichLuy() + diemCong);
            khachHangRepo.save(kh);

            log.info("Don #{} tao thanh cong - {} - {}d", hd.getMaHD(), tenKH, tongTien);

            return clean + "\n\n" +
                    "DON HANG #" + hd.getMaHD() + " DA TAO THANH CONG!\n" +
                    "Khach hang: " + tenKH + "\n" +
                    "SDT: " + soDT + "\n" +
                    "Dia chi: " + diaChi + "\n" +
                    "San pham: " + sp.getTenSP() + " x " + soLg + "\n" +
                    "Tong tien: " + String.format("%,.0f", tongTien) + "d\n" +
                    "Thanh toan: " + pttt + "\n" +
                    "Diem cong: +" + diemCong + "\n" +
                    "Chung toi se lien he va giao hang som nhat!";

        } catch (Exception e) {
            log.error("Loi tao don: {}", e.getMessage(), e);
            return clean + "\n\nLoi: " + e.getMessage();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CHỈ CẬP NHẬT PHƯƠNG THỨC THANH TOÁN
    // ═══════════════════════════════════════════════════════════════
    @Transactional
    private String processUpdatePTTT(String aiReply) {
        Matcher m = UPDATE_PTTT_PATTERN.matcher(aiReply);
        if (!m.find()) return aiReply;

        String clean   = aiReply.replaceAll("<<<UPDATE_PTTT:\\s*\\{[^}]*\\}\\s*>>>", "").trim();
        String jsonStr = m.group(1).trim();
        log.info("Cap nhat PTTT: {}", jsonStr);

        try {
            JsonNode n   = mapper.readTree(jsonStr);
            int    maHD  = n.path("maHD").asInt(0);
            String pttt  = n.path("pttt").asText("").trim();

            if (maHD == 0 || pttt.isEmpty()) {
                return clean + "\n\nKhong tim thay thong tin can cap nhat!";
            }

            return hoaDonRepo.findById(maHD).map(hd -> {
                String ptttCu = hd.getPhuongThucTT();
                hd.setPhuongThucTT(pttt);
                hoaDonRepo.save(hd);
                log.info("Cap nhat HoaDon #{}: {} -> {}", maHD, ptttCu, pttt);

                boolean isCK = pttt.toLowerCase().contains("chuyen khoan");
                return clean + "\n\n" +
                        "DA CAP NHAT PHUONG THUC THANH TOAN!\n" +
                        "Ma don hang: #" + maHD + "\n" +
                        "Phuong thuc moi: " + pttt + "\n" +
                        (isCK ? "CHUYEN_KHOAN:maHD=" + maHD + ":tongTien=" + hd.getTongThanhToan().intValue() : "") +
                        "\nDon hang khong thay doi, chi doi phuong thuc thanh toan.";

            }).orElse(clean + "\n\nKhong tim thay don hang #" + maHD + "!");

        } catch (Exception e) {
            log.error("Loi cap nhat PTTT: {}", e.getMessage(), e);
            return clean + "\n\nLoi cap nhat: " + e.getMessage();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPER
    // ═══════════════════════════════════════════════════════════════
    /** Rút gọn trạng thái hóa đơn cho dễ đọc trong chat */
    private String trangThaiShort(String tt) {
        if (tt == null) return "?";
        return switch (tt) {
            case "Hoàn thành"  -> "[HOAN THANH]";
            case "Đã hủy"      -> "[DA HUY]";
            case "Đang xử lý"  -> "[DANG XU LY]";
            default            -> "[" + tt.toUpperCase() + "]";
        };
    }

    public List<KhuyenMai> getKhuyenMai() {
        return khuyenMaiRepo.findByTrangThai("Đang áp dụng");
    }

    public List<SanPham> getSanPham() {
        return sanPhamRepo.findByTrangThai(TRANG_THAI_DANG_BAN);
    }

    public List<SanPham> getAllSanPham() {
        return sanPhamRepo.findAll();
    }
}