package com.uniport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_my_page_preferences")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UserMyPagePreference extends AuditableEntity {

    @Id
    private Long userId;

    @Column(length = 500)
    private String bio;

    @Column(length = 50)
    private String selectedCharacterCode;

    @Column
    private Boolean pushEnabled;
}
