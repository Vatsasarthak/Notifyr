package com.yash.Notifyr.dto;

import com.yash.Notifyr.entity.RecipientStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipientStatusUpdateRequest {

    @NotNull(message = "status is required")
    private RecipientStatus status;
}
