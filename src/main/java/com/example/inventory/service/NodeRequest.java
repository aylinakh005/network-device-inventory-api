package com.example.inventory.service;

/**
 * Raw, unvalidated input for creating or replacing a node. All fields are strings so that
 * the service, not the transport layer, decides what is valid.
 */
public record NodeRequest(String id, String name, String type, String status, String ipAddress) { }
