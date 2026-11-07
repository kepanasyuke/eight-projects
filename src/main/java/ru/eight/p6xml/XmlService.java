package ru.eight.p6xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import ru.eight.common.ApiException;
import ru.eight.p6xml.model.InvoiceXml;

/**
 * РџСЂРѕРµРєС‚ 6. XML/XSD: СЃС…РµРјР° СЃ include, РІР°Р»РёРґР°С†РёСЏ С‡РµСЂРµР· JAXP, JAXB-СЂР°Р·Р±РѕСЂ,
 * Р·Р°С‰РёС‚Р° РѕС‚ XXE Рё РІРЅРµС€РЅРёС… СЃСѓС‰РЅРѕСЃС‚РµР№.
 */
@Service
public class XmlService {

    private static final Logger LOG = LoggerFactory.getLogger(XmlService.class);
    private static final String XSD_PATH = "/xsd/invoice.xsd";

    private final Schema schema;
    private final JAXBContext context;

    public XmlService() {
        this.schema = loadSchema();
        this.context = createContext();
    }

    public ValidationReport validate(String xml) {
        List<String> errors = new ArrayList<>();
        SchemaValidationCollector collector = new SchemaValidationCollector(errors);
        Validator validator = this.schema.newValidator();
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(collector);
            validator.validate(new javax.xml.transform.stream.StreamSource(new StringReader(xml)));
        } catch (SAXException ex) {
            errors.add("XML РЅРµ СЂР°Р·РѕР±СЂР°РЅ: " + ex.getMessage());
        } catch (java.io.IOException ex) {
            errors.add("РћС€РёР±РєР° С‡С‚РµРЅРёСЏ: " + ex.getMessage());
        }
        ValidationReport report = new ValidationReport(errors.isEmpty(), List.copyOf(errors), xml.length(),
                "Р’Р°Р»РёРґР°С†РёСЏ РїРѕ XSD СЃ РѕРіСЂР°РЅРёС‡РµРЅРёРµРј РІРЅРµС€РЅРёС… СЃС…РµРј Рё DTD");
        return report;
    }

    public ParseResult parse(String xml) {
        ValidationReport validation = validate(xml);
        ParsedInvoice invoice = null;
        String message = "Р”РѕРєСѓРјРµРЅС‚ СЂР°Р·РѕР±СЂР°РЅ";
        if (validation.valid()) {
            invoice = readInvoice(xml);
        } else {
            message = "Р”РѕРєСѓРјРµРЅС‚ РЅРµ РїСЂРѕС€С‘Р» РІР°Р»РёРґР°С†РёСЋ, СЂР°Р·Р±РѕСЂ РѕСЃС‚Р°РЅРѕРІР»РµРЅ";
        }
        ParseResult result = new ParseResult(validation.valid(), validation.errors(), invoice, message);
        return result;
    }

    public String build(InvoiceDraft draft) {
        InvoiceXml invoice = new InvoiceXml();
        invoice.setNumber(draft.number());
        invoice.setIssuedAt(parseDate(draft.issuedAt()));
        invoice.setSeller(new InvoiceXml.Party(draft.sellerName(), draft.sellerInn()));
        invoice.setBuyer(new InvoiceXml.Party(draft.buyerName(), draft.buyerInn()));
        invoice.setVersion(1);
        List<InvoiceXml.Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int line = 1;
        for (ItemDraft item : draft.items()) {
            BigDecimal price = item.price() == null ? BigDecimal.ZERO : item.price();
            items.add(new InvoiceXml.Item(line, item.name(), item.quantity(), price));
            total = total.add(price.multiply(BigDecimal.valueOf(item.quantity())));
            line++;
        }
        invoice.setItems(items);
        invoice.setTotal(total);
        String xml = writeInvoice(invoice);
        return xml;
    }

    public String sample() {
        InvoiceDraft draft = new InvoiceDraft(
                "IN-2026/000142",
                "2026-11-06",
                "РћРћРћ В«Р’РѕСЃРµРјСЊ РїСЂРѕРµРєС‚РѕРІВ»",
                "7701234567",
                "РђРћ В«Р—Р°РєР°Р·С‡РёРєВ»",
                "7709876543",
                List.of(
                        new ItemDraft("Р Р°Р·СЂР°Р±РѕС‚РєР° backend-РєРѕРјРїРѕРЅРµРЅС‚Р°", 1, new BigDecimal("180000.00")),
                        new ItemDraft("РРЅС‚РµРіСЂР°С†РёРѕРЅРЅС‹Рµ С‚РµСЃС‚С‹", 3, new BigDecimal("24000.00"))
                )
        );
        String xml = build(draft);
        return xml;
    }

    private ParsedInvoice readInvoice(String xml) {
        ParsedInvoice parsed = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            Unmarshaller unmarshaller = this.context.createUnmarshaller();
            Object value = unmarshaller.unmarshal(reader);
            parsed = toParsed((InvoiceXml) value);
        } catch (XMLStreamException ex) {
            LOG.warn("Р Р°Р·Р±РѕСЂ XML РѕС‚РєР»РѕРЅС‘РЅ: {}", ex.getMessage());
            throw new ApiException("XML_REJECTED",
                    "Р Р°Р·Р±РѕСЂ РѕС‚РєР»РѕРЅС‘РЅ: DTD Рё РІРЅРµС€РЅРёРµ СЃСѓС‰РЅРѕСЃС‚Рё Р·Р°РїСЂРµС‰РµРЅС‹ (" + ex.getMessage() + ")",
                    HttpStatus.BAD_REQUEST);
        } catch (JAXBException ex) {
            throw new ApiException("XML_UNMARSHAL_FAILED", "РќРµ СѓРґР°Р»РѕСЃСЊ СЂР°Р·РѕР±СЂР°С‚СЊ РґРѕРєСѓРјРµРЅС‚: " + ex.getMessage(),
                    HttpStatus.BAD_REQUEST);
        }
        return parsed;
    }

    private String writeInvoice(InvoiceXml invoice) {
        String xml = null;
        try {
            Marshaller marshaller = this.context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            marshaller.marshal(invoice, writer);
            xml = writer.toString();
        } catch (JAXBException ex) {
            throw new ApiException("XML_MARSHAL_FAILED", "РќРµ СѓРґР°Р»РѕСЃСЊ СЃС„РѕСЂРјРёСЂРѕРІР°С‚СЊ XML: " + ex.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return xml;
    }

    private ParsedInvoice toParsed(InvoiceXml invoice) {
        List<ParsedItem> items = new ArrayList<>();
        for (InvoiceXml.Item item : invoice.getItems()) {
            items.add(new ParsedItem(item.getLine(), item.getName(), item.getQuantity(), item.getPrice()));
        }
        String issuedAt = invoice.getIssuedAt() == null ? null : invoice.getIssuedAt().toXMLFormat();
        ParsedInvoice parsed = new ParsedInvoice(
                invoice.getNumber(),
                issuedAt,
                invoice.getSeller() == null ? null : invoice.getSeller().getName(),
                invoice.getSeller() == null ? null : invoice.getSeller().getInn(),
                invoice.getBuyer() == null ? null : invoice.getBuyer().getName(),
                invoice.getBuyer() == null ? null : invoice.getBuyer().getInn(),
                List.copyOf(items),
                invoice.getTotal(),
                invoice.getVersion()
        );
        return parsed;
    }

    private XMLGregorianCalendar parseDate(String value) {
        XMLGregorianCalendar calendar = null;
        try {
            calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(value);
        } catch (Exception ex) {
            throw ApiException.badRequest("INVALID_DATE", "Р”Р°С‚Р° РґРѕР»Р¶РЅР° Р±С‹С‚СЊ РІ С„РѕСЂРјР°С‚Рµ YYYY-MM-DD", List.of());
        }
        return calendar;
    }

    private static Schema loadSchema() {
        Schema schema = null;
        try (java.io.InputStream stream = XmlService.class.getResourceAsStream(XSD_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("РќРµ РЅР°Р№РґРµРЅР° СЃС…РµРјР° " + XSD_PATH);
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
            factory.setResourceResolver(new ClasspathSchemaResolver());
            javax.xml.transform.stream.StreamSource source =
                    new javax.xml.transform.stream.StreamSource(stream);
            source.setSystemId(XSD_PATH);
            schema = factory.newSchema(source);
        } catch (SAXException | java.io.IOException ex) {
            throw new IllegalStateException("РЎС…РµРјР° " + XSD_PATH + " РЅРµ Р·Р°РіСЂСѓР¶РµРЅР°", ex);
        }
        return schema;
    }

    /**
     * Р Р°Р·СЂРµС€Р°РµС‚ include/import СЃС…РµРј РёР· classpath. РќСѓР¶РµРЅ РїРѕС‚РѕРјСѓ, С‡С‚Рѕ РІ РёСЃРїРѕР»РЅСЏРµРјРѕРј jar
     * СЂРµСЃСѓСЂСЃ Р»РµР¶РёС‚ РїРѕ Р°РґСЂРµСЃСѓ РІРёРґР° jar:nested:вЂ¦, РєРѕС‚РѕСЂС‹Р№ РїР°СЂСЃРµСЂ СЃС…РµРј РѕС‚РєСЂС‹С‚СЊ РЅРµ РјРѕР¶РµС‚.
     */
    private static final class ClasspathSchemaResolver implements javax.xml.transform.URIResolver,
            org.w3c.dom.ls.LSResourceResolver {

        @Override
        public javax.xml.transform.Source resolve(String href, String base) {
            return null;
        }

        @Override
        public org.w3c.dom.ls.LSInput resolveResource(String type, String namespaceUri, String publicId,
                                                      String systemId, String baseUri) {
            org.w3c.dom.ls.LSInput input = null;
            if (systemId != null) {
                String name = systemId;
                int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                if (slash >= 0) {
                    name = name.substring(slash + 1);
                }
                java.io.InputStream stream = XmlService.class.getResourceAsStream("/xsd/" + name);
                if (stream != null) {
                    input = new ClasspathLsInput(publicId, systemId, "/xsd/", stream);
                }
            }
            return input;
        }
    }

    private static final class ClasspathLsInput implements org.w3c.dom.ls.LSInput {

        private final String publicId;
        private final String systemId;
        private final String baseUri;
        private final java.io.InputStream byteStream;
        private String stringData;

        private ClasspathLsInput(String publicId, String systemId, String baseUri,
                                 java.io.InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseUri = baseUri;
            this.byteStream = byteStream;
        }

        @Override
        public java.io.Reader getCharacterStream() {
            return null;
        }

        @Override
        public void setCharacterStream(java.io.Reader characterStream) {
            // СЃС…РµРјР° С‡РёС‚Р°РµС‚СЃСЏ РїРѕС‚РѕРєРѕРј Р±Р°Р№С‚
        }

        @Override
        public java.io.InputStream getByteStream() {
            return this.byteStream;
        }

        @Override
        public void setByteStream(java.io.InputStream byteStream) {
            // РїРѕС‚РѕРє Р·Р°РґР°РЅ РІ РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂРµ
        }

        @Override
        public String getStringData() {
            return this.stringData;
        }

        @Override
        public void setStringData(String stringData) {
            this.stringData = stringData;
        }

        @Override
        public String getSystemId() {
            return this.systemId;
        }

        @Override
        public void setSystemId(String systemId) {
            // РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ Р·Р°РґР°РЅ РІ РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂРµ
        }

        @Override
        public String getPublicId() {
            return this.publicId;
        }

        @Override
        public void setPublicId(String publicId) {
            // РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ Р·Р°РґР°РЅ РІ РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂРµ
        }

        @Override
        public String getBaseURI() {
            return this.baseUri;
        }

        @Override
        public void setBaseURI(String baseUri) {
            // Р±Р°Р·РѕРІС‹Р№ Р°РґСЂРµСЃ Р·Р°РґР°РЅ РІ РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂРµ
        }

        @Override
        public String getEncoding() {
            return "UTF-8";
        }

        @Override
        public void setEncoding(String encoding) {
            // РєРѕРґРёСЂРѕРІРєР° СЃС…РµРј С„РёРєСЃРёСЂРѕРІР°РЅР°
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            // РїСЂРѕРІРµСЂРєР° РїРѕРґРїРёСЃРё СЃС…РµРјС‹ РЅРµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ
        }
    }

    private static JAXBContext createContext() {
        JAXBContext context = null;
        try {
            context = JAXBContext.newInstance(InvoiceXml.class);
        } catch (JAXBException ex) {
            throw new IllegalStateException("JAXB-РєРѕРЅС‚РµРєСЃС‚ РЅРµ СЃРѕР·РґР°РЅ", ex);
        }
        return context;
    }

    private static final class SchemaValidationCollector implements ErrorHandler {

        private final List<String> errors;

        SchemaValidationCollector(List<String> errors) {
            this.errors = errors;
        }

        @Override
        public void warning(SAXParseException ex) {
            this.errors.add("СЃС‚СЂРѕРєР° " + ex.getLineNumber() + ": " + ex.getMessage());
        }

        @Override
        public void error(SAXParseException ex) {
            this.errors.add("СЃС‚СЂРѕРєР° " + ex.getLineNumber() + ": " + ex.getMessage());
        }

        @Override
        public void fatalError(SAXParseException ex) {
            this.errors.add("СЃС‚СЂРѕРєР° " + ex.getLineNumber() + ": " + ex.getMessage());
        }
    }

    public record ValidationReport(boolean valid, List<String> errors, int length, String comment) {
    }

    public record ParseResult(boolean valid, List<String> errors, ParsedInvoice invoice, String message) {
    }

    public record ParsedInvoice(String number, String issuedAt, String sellerName, String sellerInn,
                                String buyerName, String buyerInn, List<ParsedItem> items, BigDecimal total,
                                Integer version) {
    }

    public record ParsedItem(Integer line, String name, Integer quantity, BigDecimal price) {
    }

    public record InvoiceDraft(String number, String issuedAt, String sellerName, String sellerInn,
                               String buyerName, String buyerInn, List<ItemDraft> items) {
    }

    public record ItemDraft(String name, Integer quantity, BigDecimal price) {
    }
}
