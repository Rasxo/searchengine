package searchengine.model;

import lombok.Data;

import jakarta.persistence.*;

@Entity
@Data
@Table(name = "page")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @OneToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(columnDefinition = "TEXT NOT NULL, UNIQUE KEY pathIndex (path(512), site_id)")
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", name = "content", nullable = false)
    private String content;
}
