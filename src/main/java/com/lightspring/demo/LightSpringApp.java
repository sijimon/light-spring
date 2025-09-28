package com.lightspring.demo;

import com.lightspring.LSApplicationContext;

public class LightSpringApp {
    public static void main(String[] args) {
    	try (LSApplicationContext context = new LSApplicationContext.ApplicationContextBuilder()
    	        .basePackage("com.lightspring.demo")
    	        .preferAnnotations(false)
    	        .build()) {

    	    context.refresh();
    	    MyService svc = context.getBean(MyService.class);
    	    System.out.println(svc.sayHello());
    	}
    }
}
