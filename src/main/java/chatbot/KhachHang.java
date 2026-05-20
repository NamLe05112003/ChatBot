package chatbot;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "KhachHang")
public class KhachHang {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaKH")
    private Integer maKH;

    @Column(name = "TenKH")
    private String tenKH;

    @Column(name = "SoDienThoai", unique = true)
    private String soDienThoai;

    @Column(name = "Email")
    private String email;

    @Column(name = "DiaChi", columnDefinition = "TEXT")
    private String diaChi;

    @Column(name = "NgayDangKy")
    private LocalDateTime ngayDangKy;

    @Column(name = "DiemTichLuy")
    private Integer diemTichLuy = 0;
}
