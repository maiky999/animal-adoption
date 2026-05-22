package com.wsb.animaladoption.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdoptionFormEvent implements Serializable {
    private String organizationEmail;
    private String organizationName;
    private String adTitle;
    private String firstName;
    private String lastName;
    private String address;
    private String city;
    private String contactEmail;
    private String phone;
    private Integer birthYear;
}