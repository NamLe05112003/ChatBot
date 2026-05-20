package chatbot;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "HoaDon")
public class HoaDon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaHD")
    private Integer maHD;

    @Column(name = "MaNV")
    private Integer maNV;

    @Column(name = "MaKH")
    private Integer maKH;

    @Column(name = "NgayLap")
    private LocalDateTime ngayLap;

    @Column(name = "TongTienGoc")
    private BigDecimal tongTienGoc;

    @Column(name = "SoTienGiam")
    private BigDecimal soTienGiam;

    @Column(name = "DiemSuDung")
    private Integer diemSuDung = 0;

    @Column(name = "SoTienTuDiem")
    private BigDecimal soTienTuDiem = BigDecimal.ZERO;

    @Column(name = "TongThanhToan")
    private BigDecimal tongThanhToan;

    @Column(name = "PhuongThucTT")
    private String phuongThucTT;

    @Column(name = "TrangThai")
    private String trangThai;
}
