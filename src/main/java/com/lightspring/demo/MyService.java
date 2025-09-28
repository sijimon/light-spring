package com.lightspring.demo;

import com.lightspring.ComponentMarker;

public class MyService implements ComponentMarker {

    public String sayHello() {
        return "Hello from MyService!";
    }
}
