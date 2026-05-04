package com.uniport.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class EducationQuizSubmitRequestDTO {
    private Map<String, Integer> answers;
}
