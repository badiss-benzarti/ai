package com.travelmate.itinerary.service;

import com.travelmate.itinerary.dto.BudgetSummaryDto;
import com.travelmate.itinerary.dto.BudgetSummaryDto.CategoryBudget;
import com.travelmate.itinerary.dto.ExpenseDto;
import com.travelmate.itinerary.dto.ExpenseRequest;
import com.travelmate.itinerary.entity.Expense;
import com.travelmate.itinerary.entity.ItemType;
import com.travelmate.itinerary.entity.Itinerary;
import com.travelmate.itinerary.entity.ItineraryItem;
import com.travelmate.itinerary.repository.ExpenseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final ItineraryService itineraryService;
    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public BudgetSummaryDto summary(Long itineraryId) {
        Itinerary it = itineraryService.findItinerary(itineraryId);
        List<ItineraryItem> items = it.allItems();
        List<Expense> expenses = it.getExpenses();

        List<CategoryBudget> categories = Arrays.stream(ItemType.values())
                .map(type -> new CategoryBudget(type,
                        items.stream().filter(i -> i.getType() == type).mapToDouble(ItineraryItem::getEstimatedCost).sum(),
                        expenses.stream().filter(e -> e.getCategory() == type).mapToDouble(Expense::getAmount).sum()))
                .filter(c -> c.estimated() > 0 || c.spent() > 0)
                .toList();

        double estimated = it.getTotalEstimatedCost();
        double spent = expenses.stream().mapToDouble(Expense::getAmount).sum();
        return new BudgetSummaryDto(it.getBudget(), estimated, spent,
                it.getBudget() - estimated, it.getBudget() - spent, estimated > it.getBudget(), categories);
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> getExpenses(Long itineraryId) {
        itineraryService.findItinerary(itineraryId);   // 404 if it is not the user's itinerary
        return expenseRepository.findByItineraryIdOrderByDateAsc(itineraryId).stream().map(ExpenseDto::from).toList();
    }

    @Transactional
    public ExpenseDto addExpense(Long itineraryId, ExpenseRequest request) {
        Expense e = new Expense();
        e.setItinerary(itineraryService.findItinerary(itineraryId));
        e.setCategory(request.category());
        e.setAmount(request.amount());
        e.setDate(request.date() != null ? request.date() : LocalDate.now());
        e.setDescription(request.description());
        return ExpenseDto.from(expenseRepository.save(e));
    }

    @Transactional
    public void deleteExpense(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Expense " + expenseId + " not found"));
        Itinerary it = itineraryService.findItinerary(expense.getItinerary().getId());   // 404 if not the user's
        it.getExpenses().remove(expense);
    }
}
