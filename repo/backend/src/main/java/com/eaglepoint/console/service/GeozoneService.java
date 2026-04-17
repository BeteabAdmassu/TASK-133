package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.Geozone;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.repository.GeozoneRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

public class GeozoneService {
    private final GeozoneRepository geozoneRepo;
    private final ObjectMapper mapper;

    public GeozoneService(GeozoneRepository geozoneRepo) {
        this.geozoneRepo = geozoneRepo;
        this.mapper = new ObjectMapper();
    }

    public Geozone createGeozone(String name, String zipCodesJson, String streetRangesJson) {
        validateName(name);
        validateJson(zipCodesJson, "zipCodes");
        Geozone g = new Geozone();
        g.setName(name.trim());
        g.setZipCodes(zipCodesJson);
        g.setStreetRanges(streetRangesJson);
        long id = geozoneRepo.insert(g);
        return geozoneRepo.findById(id).orElseThrow();
    }

    public Geozone getGeozone(long id) {
        return geozoneRepo.findById(id).orElseThrow(() -> new NotFoundException("Geozone", id));
    }

    public PagedResult<Geozone> listGeozones(int page, int pageSize) {
        return geozoneRepo.findAll(page, pageSize);
    }

    public Geozone updateGeozone(long id, String name, String zipCodesJson, String streetRangesJson) {
        Geozone g = geozoneRepo.findById(id).orElseThrow(() -> new NotFoundException("Geozone", id));
        if (name != null) { validateName(name); g.setName(name.trim()); }
        if (zipCodesJson != null) { validateJson(zipCodesJson, "zipCodes"); g.setZipCodes(zipCodesJson); }
        if (streetRangesJson != null) g.setStreetRanges(streetRangesJson);
        geozoneRepo.update(g);
        return geozoneRepo.findById(id).orElseThrow();
    }

    public Optional<Geozone> matchByZip(String zipCode, String streetAddress) {
        List<Geozone> candidates = geozoneRepo.findByZipCode(zipCode);
        if (candidates.isEmpty()) return Optional.empty();
        return Optional.of(candidates.get(0));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("name", "Geozone name is required");
        }
    }

    private void validateJson(String json, String field) {
        if (json == null || json.isBlank()) {
            throw new ValidationException(field, field + " is required");
        }
        try {
            mapper.readTree(json);
        } catch (Exception e) {
            throw new ValidationException(field, field + " must be valid JSON");
        }
    }
}
