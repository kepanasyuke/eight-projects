package ru.eight.p6xml.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * JAXB-модель счёта. Пространство имён задано явно, поля помечены namespace —
 * иначе при elementFormDefault="qualified" элементы уедут в пустое пространство.
 */
@XmlRootElement(name = "invoice", namespace = InvoiceXml.NS)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "invoice", namespace = InvoiceXml.NS,
        propOrder = {"number", "issuedAt", "seller", "buyer", "items", "total"})
public class InvoiceXml {

    public static final String NS = "urn:eight:invoice";

    @XmlElement(namespace = NS, required = true)
    private String number;

    @XmlElement(namespace = NS)
    private XMLGregorianCalendar issuedAt;

    @XmlElement(namespace = NS)
    private Party seller;

    @XmlElement(namespace = NS)
    private Party buyer;

    @XmlElementWrapper(name = "items", namespace = NS)
    @XmlElement(name = "item", namespace = NS)
    private List<Item> items = new ArrayList<>();

    @XmlElement(namespace = NS)
    private BigDecimal total;

    @XmlAttribute(name = "version")
    private Integer version;

    public String getNumber() {
        return this.number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public XMLGregorianCalendar getIssuedAt() {
        return this.issuedAt;
    }

    public void setIssuedAt(XMLGregorianCalendar issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Party getSeller() {
        return this.seller;
    }

    public void setSeller(Party seller) {
        this.seller = seller;
    }

    public Party getBuyer() {
        return this.buyer;
    }

    public void setBuyer(Party buyer) {
        this.buyer = buyer;
    }

    public List<Item> getItems() {
        return this.items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public BigDecimal getTotal() {
        return this.total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public Integer getVersion() {
        return this.version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "Party", namespace = NS, propOrder = {"name", "inn"})
    public static class Party {

        @XmlElement(namespace = NS)
        private String name;

        @XmlElement(namespace = NS)
        private String inn;

        public Party() {
        }

        public Party(String name, String inn) {
            this.name = name;
            this.inn = inn;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getInn() {
            return this.inn;
        }

        public void setInn(String inn) {
            this.inn = inn;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "InvoiceItem", namespace = NS, propOrder = {"name", "quantity", "price"})
    public static class Item {

        @XmlAttribute(name = "line", required = true)
        private Integer line;

        @XmlElement(namespace = NS)
        private String name;

        @XmlElement(namespace = NS)
        private Integer quantity;

        @XmlElement(namespace = NS)
        private BigDecimal price;

        public Item() {
        }

        public Item(Integer line, String name, Integer quantity, BigDecimal price) {
            this.line = line;
            this.name = name;
            this.quantity = quantity;
            this.price = price;
        }

        public Integer getLine() {
            return this.line;
        }

        public void setLine(Integer line) {
            this.line = line;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getQuantity() {
            return this.quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getPrice() {
            return this.price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }
}
