package org.example;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        CrptApi apiInstance = new CrptApi(TimeUnit.MINUTES, 10);

        try {
            var document = new Document("format", "content", "group", "type");
            var uuid = apiInstance.createDocument(document, "signature");
            System.out.printf("Success, uuid = %s\n", uuid.toString());
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }
}