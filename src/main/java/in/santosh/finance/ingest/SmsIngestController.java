package in.santosh.finance.ingest;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HTTP adapter only: validate, translate to a command, translate the result to a status code.
 * No business logic here. Authentication already happened in ApiTokenFilter.
 */
@RestController
@RequestMapping("/api/ingest")
class SmsIngestController {

    private final SmsIngestService service;

    SmsIngestController(SmsIngestService service) {
        this.service = service;
    }

    /**
     * Request DTO, separate from the entity. The API contract and the DB schema can then evolve
     * independently, and clients can't set fields they shouldn't (mass-assignment).
     * Size limits stop a buggy or hostile client from storing megabytes per "SMS".
     *
     * @param receivedAt ISO-8601 with offset, e.g. 2026-11-04T10:15:30+05:30. Optional.
     */
    record SmsIngestRequest(
            @NotBlank @Size(max = 64) String sender,
            @NotBlank @Size(max = 2000) String body,
            OffsetDateTime receivedAt) {
    }

    /**
     * 201 when a new transaction was created; 200 for every other successful outcome
     * (a retry or a duplicate is still a success for the client: that's idempotency).
     */
    @PostMapping("/sms")
    ResponseEntity<SmsIngestService.Result> ingest(@Valid @RequestBody SmsIngestRequest request) {
        var result = service.ingest(new SmsIngestService.Command(
                request.sender(),
                request.body(),
                request.receivedAt() != null ? request.receivedAt().toInstant() : null));
        var status = result.status() == SmsIngestService.Status.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
