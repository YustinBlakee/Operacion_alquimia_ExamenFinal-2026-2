package com.alquimia.infrastructure.adapter.in.web;

import com.alquimia.application.port.in.IntelligenceUseCases;
import com.alquimia.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class IntelligenceController {
    private final IntelligenceUseCases useCases;

    public IntelligenceController(IntelligenceUseCases useCases) { this.useCases = useCases; }

    public record ReadingRequest(@NotBlank String device, @NotNull Double distance,
                                 @NotEmpty @Size(max = 1000) List<String> message) {
        Reading toDomain() { return Reading.of(Device.fromId(device), distance, message); }
    }
    public record DataRequest(@NotNull Double distance, @NotEmpty @Size(max = 1000) List<String> message) { }
    public record ProcessingRequest(@NotEmpty @Size(max = 3) List<@NotNull @Valid ReadingRequest> devices) { }
    public record ReadingResponse(String device, double distance, List<String> message) {
        static ReadingResponse from(Reading reading) {
            return new ReadingResponse(reading.device().id(), reading.distance(), reading.message());
        }
    }
    public record DeviceResponse(String device, Position position) { }

    @GetMapping("/devices")
    public List<DeviceResponse> devices() {
        return java.util.Arrays.stream(Device.values())
            .map(device -> new DeviceResponse(device.id(), device.position())).toList();
    }

    @PostMapping("/intelligence")
    public IntelligenceResult process(@Valid @RequestBody ProcessingRequest request) {
        return useCases.process(request.devices().stream().map(ReadingRequest::toDomain).toList());
    }

    @GetMapping("/intelligence")
    public IntelligenceResult processStored() { return useCases.processStored(); }

    @GetMapping("/readings")
    public List<ReadingResponse> readings() {
        return useCases.findAll().stream().map(ReadingResponse::from).toList();
    }

    @GetMapping("/readings/{device}")
    public ReadingResponse reading(@PathVariable String device) {
        return ReadingResponse.from(useCases.find(Device.fromId(device)));
    }

    @PostMapping("/readings/{device}")
    public ResponseEntity<ReadingResponse> register(@PathVariable String device, @Valid @RequestBody DataRequest request) {
        Reading reading = useCases.register(Reading.of(Device.fromId(device), request.distance(), request.message()));
        return ResponseEntity.created(URI.create("/api/readings/" + reading.device().id()))
            .body(ReadingResponse.from(reading));
    }

    @PutMapping("/readings/{device}")
    public ReadingResponse replace(@PathVariable String device, @Valid @RequestBody DataRequest request) {
        return ReadingResponse.from(useCases.replace(Reading.of(Device.fromId(device), request.distance(), request.message())));
    }

    @PatchMapping("/readings/{device}")
    public ReadingResponse update(@PathVariable String device, @RequestBody PatchRequest request) {
        request.validate();
        return ReadingResponse.from(useCases.update(Device.fromId(device), request.distance(), request.message()));
    }

    @DeleteMapping("/readings/{device}")
    public ResponseEntity<Void> delete(@PathVariable String device) {
        useCases.delete(Device.fromId(device));
        return ResponseEntity.noContent().build();
    }
}
