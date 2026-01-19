package org.gstk;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FailedTiles {
    public final File file;
    public Fails fails;

    public FailedTiles(File file) throws JAXBException {
        this.file = file;

        fails = new Fails();
        fails.fails = new ArrayList<>();
        read();
    }

    public void addFailedTile(int zoom, int x, int y, FailType type, String url) {
        Fail fail = new Fail();
        fail.timestamp = LocalDateTime.now().toString();
        fail.zoom = zoom;
        fail.x = x;
        fail.y = y;
        fail.type = type.name;
        fail.url = url;

        fails.fails.add(fail);
    }

    public void write() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Fails.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(fails, file);
    }

    private void read() throws JAXBException {
        if (file.exists()) {
            JAXBContext context = JAXBContext.newInstance(Fails.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            this.fails = (Fails) unmarshaller.unmarshal(file);
        }
    }

    @XmlRootElement(name="fails")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Fails {
        @XmlElement(name="fail")
        public List<Fail> fails;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Fail {
        public String timestamp;
        public int zoom;
        public int x;
        public int y;
        public String type;
        public String url;
    }

    public enum FailType {
        DOWNLOAD("download"), WRITE("write");

        final String name;
        FailType(String name) {
            this.name = name;
        }
    }
}
