package com.wsb.animaladoption.dto;

import com.wsb.animaladoption.enums.AnimalAgeEnum;
import com.wsb.animaladoption.enums.AnimalSexEnum;
import com.wsb.animaladoption.enums.AnimalSizeEnum;
import com.wsb.animaladoption.model.Ad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class AdCreateDto {
    @NotBlank(message = "{validation.title.required}")
    @Size(min = 10, max = 150, message = "{validation.title.size}")
    private String title;

    @NotBlank(message = "{validation.description.required}")
    @Size(min = 20, message = "{validation.ad.description.size}")
    private String description;

    @NotNull(message = "{validation.category.required}")
    private Long categoryId;

    @NotBlank(message = "{validation.location.required}")
    private String location;

    private List<MultipartFile> imageFiles;

    // --- Information about the animal (optional - null means "not specified") ---

    private AnimalAgeEnum age;

    private AnimalSexEnum sex;

    private AnimalSizeEnum size;

    // Checkboxes: an unchecked box is sent as false (thanks to the hidden "_field" marker added by th:field)
    private Boolean vaccinated;

    private Boolean sterilized;

    private Boolean chipped;

    private Boolean goodWithKids;

    private Boolean goodWithAnimals;

    /** Fills the form with the current listing data (edit mode). */
    public static AdCreateDto from(Ad ad) {
        AdCreateDto dto = new AdCreateDto();
        dto.setTitle(ad.getTitle());
        dto.setDescription(ad.getDescription());
        dto.setCategoryId(ad.getCategory().getId());
        dto.setLocation(ad.getLocation());
        dto.setAge(ad.getAge());
        dto.setSex(ad.getSex());
        dto.setSize(ad.getSize());
        dto.setVaccinated(ad.getVaccinated());
        dto.setSterilized(ad.getSterilized());
        dto.setChipped(ad.getChipped());
        dto.setGoodWithKids(ad.getGoodWithKids());
        dto.setGoodWithAnimals(ad.getGoodWithAnimals());
        return dto;
    }

    /** Copies the animal information from the form to the listing (used both when creating and editing). */
    public void applyAnimalDetailsTo(Ad ad) {
        ad.setAge(age);
        ad.setSex(sex);
        ad.setSize(size);
        ad.setVaccinated(vaccinated);
        ad.setSterilized(sterilized);
        ad.setChipped(chipped);
        ad.setGoodWithKids(goodWithKids);
        ad.setGoodWithAnimals(goodWithAnimals);
    }
}
