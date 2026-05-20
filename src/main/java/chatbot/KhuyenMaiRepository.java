package chatbot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface KhuyenMaiRepository extends JpaRepository<KhuyenMai, Integer> {

    // Lấy danh sách khuyến mãi theo trạng thái — dùng cho web và chatbot
    List<KhuyenMai> findByTrangThai(String trangThai);

    // Tóm tắt khuyến mãi đang áp dụng (dùng trong system prompt của AI)
    @Query("SELECT CONCAT(k.tenKM, ' | ', k.loaiGiam, ': ', k.giaTri, " +
            "CASE WHEN k.loaiGiam = 'Phần trăm' THEN '%' ELSE 'd' END, " +
            "' | Don tu ', k.donHangToiThieu, 'd') " +
            "FROM KhuyenMai k WHERE k.trangThai = 'Đang áp dụng'")
    List<String> getSummary();
}