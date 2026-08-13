package com.finanzapp.app.data.sharedexpenses;

import com.finanzapp.app.data.model.Settlement;
import com.finanzapp.app.data.model.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BalanceCalculator {

    public static class SuggestedPayment {
        public String fromUid;
        public String toUid;
        public double amount;

        public SuggestedPayment(String fromUid, String toUid, double amount) {
            this.fromUid = fromUid;
            this.toUid = toUid;
            this.amount = amount;
        }
    }

    /**
     * Calcula los saldos netos de cada miembro de la familia.
     * Saldo positivo = le deben dinero.
     * Saldo negativo = debe dinero.
     */
    public static Map<String, Double> calculateNetBalances(List<Transaction> transactions, List<Settlement> settlements) {
        Map<String, Double> balances = new HashMap<>();

        // 1. Procesar transacciones con reparto
        for (Transaction t : transactions) {
            if (t.getSplitAmongUids() == null || t.getSplitAmongUids().isEmpty()) {
                continue;
            }

            String payer = t.getPaidByUid();
            double amount = t.getAmount();

            // Quien pagó sumó a su saldo lo que adelantó
            Double currentPayerBalance = balances.get(payer);
            balances.put(payer, (currentPayerBalance != null ? currentPayerBalance : 0.0) + amount);

            // Restar lo que le corresponde a cada uno
            if ("custom".equals(t.getSplitMode()) && t.getSplitAmounts() != null) {
                for (Map.Entry<String, Double> entry : t.getSplitAmounts().entrySet()) {
                    String uid = entry.getKey();
                    double share = entry.getValue();
                    Double currentUidBalance = balances.get(uid);
                    balances.put(uid, (currentUidBalance != null ? currentUidBalance : 0.0) - share);
                }
            } else {
                // Reparto a partes iguales
                int n = t.getSplitAmongUids().size();
                double share = Math.floor((amount / n) * 100.0) / 100.0;
                double remainder = Math.round((amount - (share * n)) * 100.0) / 100.0;

                for (int i = 0; i < n; i++) {
                    String uid = t.getSplitAmongUids().get(i);
                    double userShare = share + (i == 0 ? remainder : 0);
                    Double currentUidBalance = balances.get(uid);
                    balances.put(uid, (currentUidBalance != null ? currentUidBalance : 0.0) - userShare);
                }
            }
        }

        // 2. Procesar liquidaciones (settlements)
        for (Settlement s : settlements) {
            Double currentFromBalance = balances.get(s.getFromUid());
            balances.put(s.getFromUid(), (currentFromBalance != null ? currentFromBalance : 0.0) + s.getAmount());
            
            Double currentToBalance = balances.get(s.getToUid());
            balances.put(s.getToUid(), (currentToBalance != null ? currentToBalance : 0.0) - s.getAmount());
        }

        // Limpiar redondeos ínfimos
        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            if (Math.abs(entry.getValue()) < 0.001) {
                balances.put(entry.getKey(), 0.0);
            }
        }

        return balances;
    }

    /**
     * Sugiere una lista simplificada de pagos para dejar todos los saldos en cero.
     * Utiliza un algoritmo greedy que minimiza el número de transacciones.
     */
    public static List<SuggestedPayment> calculateSuggestedPayments(Map<String, Double> netBalances) {
        List<SuggestedPayment> payments = new ArrayList<>();

        List<MemberBalance> debtors = new ArrayList<>();
        List<MemberBalance> creditors = new ArrayList<>();

        for (Map.Entry<String, Double> entry : netBalances.entrySet()) {
            double balance = entry.getValue();
            if (balance < -0.009) {
                debtors.add(new MemberBalance(entry.getKey(), balance));
            } else if (balance > 0.009) {
                creditors.add(new MemberBalance(entry.getKey(), balance));
            }
        }

        // Algoritmo Greedy
        int dIdx = 0;
        int cIdx = 0;

        // Ordenar para intentar saldar deudas grandes primero (opcional, ayuda a simplificar)
        debtors.sort((a, b) -> Double.compare(a.balance, b.balance)); // Más negativo primero
        creditors.sort((a, b) -> Double.compare(b.balance, a.balance)); // Más positivo primero

        while (dIdx < debtors.size() && cIdx < creditors.size()) {
            MemberBalance debtor = debtors.get(dIdx);
            MemberBalance creditor = creditors.get(cIdx);

            double amountToPay = Math.min(Math.abs(debtor.balance), creditor.balance);
            amountToPay = Math.round(amountToPay * 100.0) / 100.0;

            if (amountToPay > 0) {
                payments.add(new SuggestedPayment(debtor.uid, creditor.uid, amountToPay));
            }

            debtor.balance += amountToPay;
            creditor.balance -= amountToPay;

            if (Math.abs(debtor.balance) < 0.009) dIdx++;
            if (Math.abs(creditor.balance) < 0.009) cIdx++;
        }

        return payments;
    }

    private static class MemberBalance {
        String uid;
        double balance;

        MemberBalance(String uid, double balance) {
            this.uid = uid;
            this.balance = balance;
        }
    }
}
