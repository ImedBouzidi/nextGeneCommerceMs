package tn.iteam.paymentservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tn.iteam.paymentservice.enums.InvoiceItemType;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class InvoiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String planName;
    private String description;
    private Long amountInCents;
    private String currency;

    @Enumerated(EnumType.STRING)
    private InvoiceItemType invoiceItemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;
}
