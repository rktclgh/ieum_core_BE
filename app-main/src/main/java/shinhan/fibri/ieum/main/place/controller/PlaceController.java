package shinhan.fibri.ieum.main.place.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shinhan.fibri.ieum.main.place.dto.GeocodeResponse;
import shinhan.fibri.ieum.main.place.dto.PlaceSearchResponse;
import shinhan.fibri.ieum.main.place.dto.ReverseGeocodeResponse;
import shinhan.fibri.ieum.main.place.service.PlaceService;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

	private final PlaceService placeService;

	@GetMapping("/search")
	public ResponseEntity<PlaceSearchResponse> search(
		@RequestParam(required = false) String query,
		@RequestParam(required = false) Double lat,
		@RequestParam(required = false) Double lng
	) {
		String normalizedQuery = PlaceRequestValidator.normalizeQuery(query);
		PlaceRequestValidator.validateOptionalCoordinates(lat, lng);
		return ResponseEntity.ok(placeService.search(normalizedQuery, lat, lng));
	}

	@GetMapping("/geocode")
	public ResponseEntity<GeocodeResponse> geocode(@RequestParam(required = false) String query) {
		return ResponseEntity.ok(placeService.geocode(PlaceRequestValidator.normalizeQuery(query)));
	}

	@GetMapping("/reverse-geocode")
	public ResponseEntity<ReverseGeocodeResponse> reverseGeocode(
		@RequestParam(required = false) Double lat,
		@RequestParam(required = false) Double lng
	) {
		PlaceRequestValidator.validateRequiredCoordinates(lat, lng);
		return ResponseEntity.ok(placeService.reverseGeocode(lat, lng));
	}
}
