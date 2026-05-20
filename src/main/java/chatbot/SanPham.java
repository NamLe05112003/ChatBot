package chatbot;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "SanPham")
public class SanPham {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaSP")
    private Integer maSP;

    @Column(name = "TenSP")
    private String tenSP;

    @Column(name = "GiaBan")
    private BigDecimal giaBan;

    @Column(name = "ThoiGianBaoHanh")
    private Integer thoiGianBaoHanh;

    @Column(name = "SoLuongTon")
    private Integer soLuongTon;

    @Column(name = "HinhAnh")
    private String hinhAnh;

    @Column(name = "TrangThai")
    private String trangThai;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "MaDM")
    private DanhMuc danhMuc;
}