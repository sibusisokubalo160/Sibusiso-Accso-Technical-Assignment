package com.accso.shipmentservive.web;

import com.accso.shipmentservive.exception.ShipmentNotFoundException;
import com.accso.shipmentservive.web.dto.ShipmentEventResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * A request body that isn't valid JSON at all (or is missing entirely) never reaches
     * {@code ShipmentEventValidator} - Jackson fails before the controller method runs. Reported
     * as INVALID/400 for consistency, though no identifying fields can be echoed since nothing
     * could be parsed.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ShipmentEventResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ShipmentEventResponse.invalid(null, null, "Request body is missing or is not valid JSON"));
    }

    @ExceptionHandler(ShipmentNotFoundException.class)
    public ResponseEntity<Void> handleShipmentNotFound(ShipmentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
