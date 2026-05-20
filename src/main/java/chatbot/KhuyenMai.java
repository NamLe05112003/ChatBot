package chatbot;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "KhuyenMai")
public class KhuyenMai {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaKM")
    private Integer maKM;

    @Column(name = "TenKM", unique = true)
    private String tenKM;          // Mã code: SALE10, HOME15...

    @Column(name = "LoaiGiam")
    private String loaiGiam;       // "Phần trăm" hoặc "Tiền mặt"

    @Column(name = "GiaTri")
    private BigDecimal giaTri;     // % hoặc số tiền

    @Column(name = "GiamToiDa")
    private BigDecimal giamToiDa;  // Giới hạn giảm tối đa (cho loại %)

    @Column(name = "DonHangToiThieu")
    private BigDecimal donHangToiThieu; // Giá trị đơn tối thiểu

    @Column(name = "NgayBatDau")
    private LocalDateTime ngayBatDau;

    @Column(name = "NgayKetThuc")
    private LocalDateTime ngayKetThuc;

    @Column(name = "TrangThai")
    private String trangThai;      // "Đang áp dụng", "Hết hạn", "Tạm dừng"
}