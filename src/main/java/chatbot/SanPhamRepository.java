package chatbot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SanPhamRepository extends JpaRepository<SanPham, Integer> {

    List<SanPham> findByTrangThai(String trangThai);

    List<SanPham> findByTenSPContainingIgnoreCase(String keyword);

    @Query("SELECT s FROM SanPham s WHERE s.trangThai = 'Đang bán'")
    List<SanPham> findDangBan();
}