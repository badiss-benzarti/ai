package com.travelmate.itinerary.controller;

import com.travelmate.itinerary.dto.BudgetSummaryDto;
import com.travelmate.itinerary.dto.ExpenseDto;
import com.travelmate.itinerary.dto.ExpenseRequest;
import com.travelmate.itinerary.service.BudgetService;
import com.travelmate.itinerary.service.PdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;
    private final PdfService pdfService;

    @GetMapping("/{id}/budget")
    public BudgetSummaryDto summary(@PathVariable Long id) {
        return budgetService.summary(id);
    }

    @GetMapping("/{id}/expenses")
    public List<ExpenseDto> expenses(@PathVariable Long id) {
        return budgetService.getExpenses(id);
    }

    @PostMapping("/{id}/expenses")
    public ExpenseDto addExpense(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return budgetService.addExpense(id, request);
    }

    @DeleteMapping("/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long expenseId) {
        budgetService.deleteExpense(expenseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=itinerary-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfService.export(id));
    }
}
