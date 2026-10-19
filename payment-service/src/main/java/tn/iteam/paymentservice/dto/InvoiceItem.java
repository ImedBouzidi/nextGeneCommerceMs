package tn.iteam.paymentservice.dto;

import lombok.*;
import tn.iteam.paymentservice.enums.InvoiceItemType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItem {
    private String planName;
    private String description;
    private Long amountInCents;
    private String currency;
    private InvoiceItemType invoiceItemType;
}
