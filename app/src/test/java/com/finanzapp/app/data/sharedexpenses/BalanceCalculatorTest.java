package com.finanzapp.app.data.sharedexpenses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.finanzapp.app.data.model.Settlement;
import com.finanzapp.app.data.model.Transaction;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BalanceCalculatorTest {

    @Test
    public void testCalculateNetBalances_EqualSplit() {
        List<Transaction> transactions = new ArrayList<>();
        
        // Transaction 1: Alice pays 30, split among Alice, Bob, Charlie (10 each)
        Transaction t1 = new Transaction();
        t1.setAmount(30.0);
        t1.setPaidByUid("Alice");
        t1.setSplitAmongUids(Arrays.asList("Alice", "Bob", "Charlie"));
        t1.setSplitMode("equal");
        t1.setType("expense");
        transactions.add(t1);

        Map<String, Double> balances = BalanceCalculator.calculateNetBalances(transactions, new ArrayList<>());

        // Alice: +30 (paid) - 10 (share) = +20
        // Bob: -10 (share) = -10
        // Charlie: -10 (share) = -10
        Double aliceBal = balances.get("Alice");
        Double bobBal = balances.get("Bob");
        Double charlieBal = balances.get("Charlie");
        
        assertTrue(aliceBal != null && Math.abs(aliceBal - 20.0) < 0.001);
        assertTrue(bobBal != null && Math.abs(bobBal + 10.0) < 0.001);
        assertTrue(charlieBal != null && Math.abs(charlieBal + 10.0) < 0.001);
    }

    @Test
    public void testCalculateNetBalances_CustomSplit() {
        List<Transaction> transactions = new ArrayList<>();
        
        // Transaction 1: Alice pays 50, Alice gets 10, Bob gets 40
        Transaction t1 = new Transaction();
        t1.setAmount(50.0);
        t1.setPaidByUid("Alice");
        t1.setSplitAmongUids(Arrays.asList("Alice", "Bob"));
        t1.setSplitMode("custom");
        Map<String, Double> customSplit = new HashMap<>();
        customSplit.put("Alice", 10.0);
        customSplit.put("Bob", 40.0);
        t1.setSplitAmounts(customSplit);
        t1.setType("expense");
        transactions.add(t1);

        Map<String, Double> balances = BalanceCalculator.calculateNetBalances(transactions, new ArrayList<>());

        // Alice: +50 (paid) - 10 (share) = +40
        // Bob: -40 (share) = -40
        Double aliceBal = balances.get("Alice");
        Double bobBal = balances.get("Bob");
        
        assertTrue(aliceBal != null && Math.abs(aliceBal - 40.0) < 0.001);
        assertTrue(bobBal != null && Math.abs(bobBal + 40.0) < 0.001);
    }

    @Test
    public void testCalculateNetBalances_WithSettlements() {
        List<Transaction> transactions = new ArrayList<>();
        Transaction t1 = new Transaction();
        t1.setAmount(30.0);
        t1.setPaidByUid("Alice");
        t1.setSplitAmongUids(Arrays.asList("Alice", "Bob", "Charlie"));
        t1.setSplitMode("equal");
        t1.setType("expense");
        transactions.add(t1);

        List<Settlement> settlements = new ArrayList<>();
        // Bob pays Alice 5
        settlements.add(new Settlement("s1", "Bob", "Alice", 5.0, "Note", "Bob", null));

        Map<String, Double> balances = BalanceCalculator.calculateNetBalances(transactions, settlements);

        // Alice: +20 - 5 = +15
        // Bob: -10 + 5 = -5
        // Charlie: -10
        Double aliceBal = balances.get("Alice");
        Double bobBal = balances.get("Bob");
        Double charlieBal = balances.get("Charlie");
        
        assertTrue(aliceBal != null && Math.abs(aliceBal - 15.0) < 0.001);
        assertTrue(bobBal != null && Math.abs(bobBal + 5.0) < 0.001);
        assertTrue(charlieBal != null && Math.abs(charlieBal + 10.0) < 0.001);
    }

    @Test
    public void testCalculateSuggestedPayments() {
        Map<String, Double> netBalances = new HashMap<>();
        netBalances.put("Alice", 20.0);
        netBalances.put("Bob", -10.0);
        netBalances.put("Charlie", -10.0);

        List<BalanceCalculator.SuggestedPayment> payments = BalanceCalculator.calculateSuggestedPayments(netBalances);

        assertEquals(2, payments.size());
        
        // Possible outcome: Bob pays Alice 10, Charlie pays Alice 10
        // Or Charlie pays Alice 10, Bob pays Alice 10
        boolean bobPaidAlice = false;
        boolean charliePaidAlice = false;
        
        for (BalanceCalculator.SuggestedPayment p : payments) {
            if ("Bob".equals(p.fromUid) && "Alice".equals(p.toUid) && Math.abs(p.amount - 10.0) < 0.001) {
                bobPaidAlice = true;
            }
            if ("Charlie".equals(p.fromUid) && "Alice".equals(p.toUid) && Math.abs(p.amount - 10.0) < 0.001) {
                charliePaidAlice = true;
            }
        }
        
        assertTrue(bobPaidAlice);
        assertTrue(charliePaidAlice);
    }

    @Test
    public void testCalculateNetBalances_SettlementRecovery() {
        List<Transaction> transactions = new ArrayList<>();
        Transaction t1 = new Transaction();
        t1.setAmount(30.0);
        t1.setPaidByUid("Alice");
        t1.setSplitAmongUids(Arrays.asList("Alice", "Bob", "Charlie"));
        t1.setSplitMode("equal");
        t1.setType("expense");
        transactions.add(t1);

        List<Settlement> settlements = new ArrayList<>();
        Settlement s1 = new Settlement("s1", "Bob", "Alice", 5.0, "Note", "Bob", null);
        settlements.add(s1);

        // With settlement
        Map<String, Double> balancesWithSettlement = BalanceCalculator.calculateNetBalances(transactions, settlements);
        assertEquals(-5.0, balancesWithSettlement.get("Bob"), 0.001);

        // Remove settlement (Recovery)
        settlements.remove(s1);
        Map<String, Double> balancesAfterRemoval = BalanceCalculator.calculateNetBalances(transactions, settlements);

        // Should be back to original -10
        assertEquals(-10.0, balancesAfterRemoval.get("Bob"), 0.001);
        assertEquals(20.0, balancesAfterRemoval.get("Alice"), 0.001);
    }
    
    @Test
    public void testCalculateNetBalances_EqualSplitRemainder() {
        List<Transaction> transactions = new ArrayList<>();
        
        // Transaction 1: Alice pays 10, split among Alice, Bob, Charlie (3.33 each, one gets 3.34)
        Transaction t1 = new Transaction();
        t1.setAmount(10.0);
        t1.setPaidByUid("Alice");
        t1.setSplitAmongUids(Arrays.asList("Alice", "Bob", "Charlie"));
        t1.setSplitMode("equal");
        t1.setType("expense");
        transactions.add(t1);

        Map<String, Double> balances = BalanceCalculator.calculateNetBalances(transactions, new ArrayList<>());

        // Sum of negative balances must be exactly 10
        Double aliceBal = balances.get("Alice");
        Double bobBal = balances.get("Bob");
        Double charlieBal = balances.get("Charlie");
        
        assertTrue(aliceBal != null && bobBal != null && charlieBal != null);
        
        double sum = aliceBal + bobBal + charlieBal;
        // Alice: +10 - 3.34 = 6.66
        // Bob: -3.33
        // Charlie: -3.33
        // Total: 6.66 - 3.33 - 3.33 = 0.0
        assertEquals(0.0, sum, 0.001);
    }
}
