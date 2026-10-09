package com.booking.catalog.service;

import java.util.List;

import com.booking.catalog.domain.Airport;
import com.booking.catalog.domain.Extra;
import com.booking.catalog.domain.VehicleCategory;
import com.booking.catalog.repository.CatalogRepository;
import com.booking.platform.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What customers can book, and admin changes to it. */
@Service
public class CatalogService {

    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<VehicleCategory> vehicleCategories() {
        return repository.activeCategories();
    }

    @Transactional(readOnly = true)
    public List<Extra> extras() {
        return repository.activeExtras();
    }

    @Transactional(readOnly = true)
    public List<Airport> airports() {
        return repository.activeAirports();
    }

    /** Creates or replaces a category; inactive ones disappear from booking without breaking old bookings. */
    @Transactional
    public VehicleCategory save(VehicleCategory category) {
        requireCode(category.code());
        repository.upsert(category);
        return category;
    }

    @Transactional
    public Extra save(Extra extra) {
        requireCode(extra.code());
        repository.upsert(extra);
        return extra;
    }

    private static void requireCode(String code) {
        if (!VehicleCategory.isValidCode(code)) {
            throw ApiException.unprocessable("invalid_code", "Codes are upper case letters, digits and _, e.g. PEOPLE_CARRIER.");
        }
    }
}
