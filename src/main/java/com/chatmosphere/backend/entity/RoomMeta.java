package com.chatmosphere.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "room_metadata")
@Getter
@Setter
public class RoomMeta extends Audit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roomId;

    private String roomName;

    private boolean isGroup;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "room_meta_participants",
        joinColumns = @JoinColumn(name = "room_meta_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> participants = new HashSet<>();
}
