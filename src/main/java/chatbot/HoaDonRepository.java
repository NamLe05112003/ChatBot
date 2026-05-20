package chatbot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HoaDonRepository extends JpaRepository<HoaDon, Integer> {

    /**
     * Lấy tất cả hóa đơn của một khách hàng,
     * sắp xếp từ mới nhất đến cũ nhất.
     * Dùng cho tính năng tra cứu lịch sử mua hàng qua chatbot.
     */
    List<HoaDon> findByMaKHOrderByNgayLapDesc(Integer maKH);
}