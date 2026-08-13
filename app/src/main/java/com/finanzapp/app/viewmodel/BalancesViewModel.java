package com.finanzapp.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.data.model.Settlement;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.data.repository.AuthRepository;
import com.finanzapp.app.data.repository.FamilyRepository;
import com.finanzapp.app.data.repository.SettlementRepository;
import com.finanzapp.app.data.repository.TransactionRepository;
import com.finanzapp.app.data.sharedexpenses.BalanceCalculator;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.util.SingleLiveEvent;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BalancesViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final FamilyRepository familyRepository;
    private final TransactionRepository transactionRepository;
    private final SettlementRepository settlementRepository;

    private final MutableLiveData<List<Member>> members = new MutableLiveData<>();
    private final MutableLiveData<String> currencyCode = new MutableLiveData<>("EUR");
    private final LiveData<List<Transaction>> transactions;
    private final LiveData<Result<List<Settlement>>> settlements;
    
    private final MediatorLiveData<BalancesData> balancesData = new MediatorLiveData<>();
    private final SingleLiveEvent<Result<Settlement>> settlementResult = new SingleLiveEvent<>();
    
    private ListenerRegistration membersListener;
    private final Runnable signOutCleanup = this::stopListening;

    public static class BalancesData {
        public final Map<String, Double> netBalances;
        public final List<BalanceCalculator.SuggestedPayment> suggestedPayments;
        public final List<Member> members;

        public BalancesData(Map<String, Double> netBalances, List<BalanceCalculator.SuggestedPayment> suggestedPayments, List<Member> members) {
            this.netBalances = netBalances;
            this.suggestedPayments = suggestedPayments;
            this.members = members;
        }
    }

    public BalancesViewModel(AuthRepository authRepository, 
                             FamilyRepository familyRepository, 
                             TransactionRepository transactionRepository, 
                             SettlementRepository settlementRepository,
                             String familyId) {
        this.authRepository = authRepository;
        this.familyRepository = familyRepository;
        this.transactionRepository = transactionRepository;
        this.settlementRepository = settlementRepository;
        authRepository.registerPreSignOutCleanup(signOutCleanup);

        // We fetch ALL transactions for shared expenses (no period filter requested by default in Phase 18 design)
        // Reusing repository method with null filters
        transactions = transactionRepository.getTransactions(familyId, null, null, "expense", null, null, null);
        
        MutableLiveData<Result<List<Settlement>>> settlementsLiveData = new MutableLiveData<>();
        settlementRepository.getSettlements(familyId, settlementsLiveData::postValue);
        this.settlements = settlementsLiveData;

        fetchFamilyCurrency(familyId);
        fetchMembers(familyId);

        balancesData.addSource(transactions, t -> computeBalances());
        balancesData.addSource(settlements, s -> computeBalances());
        balancesData.addSource(members, m -> computeBalances());
    }

    private void fetchMembers(String familyId) {
        if (membersListener != null) membersListener.remove();
        membersListener = familyRepository.getMembers(familyId, result -> {
            if (result instanceof Result.Success) {
                members.setValue(((Result.Success<List<Member>>) result).getData());
            }
        });
    }

    private void fetchFamilyCurrency(String familyId) {
        familyRepository.getFamily(familyId, result -> {
            if (result instanceof Result.Success) {
                String code = ((Result.Success<com.finanzapp.app.data.model.Family>) result).getData().getCurrencyCode();
                if (code != null) {
                    currencyCode.setValue(code);
                }
            }
        });
    }

    private void computeBalances() {
        List<Transaction> tList = transactions.getValue();
        Result<List<Settlement>> sResult = settlements.getValue();
        List<Member> mList = members.getValue();

        if (tList == null || sResult == null || !(sResult instanceof Result.Success) || mList == null) {
            return;
        }

        List<Settlement> sList = ((Result.Success<List<Settlement>>) sResult).getData();
        
        Map<String, Double> netBalances = BalanceCalculator.calculateNetBalances(tList, sList);
        List<BalanceCalculator.SuggestedPayment> suggested = BalanceCalculator.calculateSuggestedPayments(netBalances);
        
        // Sort suggested payments: current user as debtor first, then creditor, then others
        String currentUserId = authRepository.getUid();
        if (currentUserId != null) {
            suggested.sort((p1, p2) -> {
                int score1 = getPaymentPriority(p1, currentUserId);
                int score2 = getPaymentPriority(p2, currentUserId);
                return Integer.compare(score1, score2);
            });
        }
        
        balancesData.setValue(new BalancesData(netBalances, suggested, mList));
    }

    private int getPaymentPriority(BalanceCalculator.SuggestedPayment p, String currentUserId) {
        if (currentUserId.equals(p.fromUid)) return 0; // Current user owes money
        if (currentUserId.equals(p.toUid)) return 1;   // Current user receives money
        return 2; // Others
    }

    public LiveData<BalancesData> getBalancesData() {
        return balancesData;
    }

    public LiveData<String> getCurrencyCode() {
        return currencyCode;
    }

    public LiveData<Result<Settlement>> getSettlementResult() {
        return settlementResult;
    }

    public LiveData<Result<List<Settlement>>> getSettlements() {
        return settlements;
    }

    public void addSettlement(String familyId, String fromUid, String toUid, double amount, String note) {
        settlementResult.setValue(new Result.Loading<>());
        settlementRepository.addSettlement(familyId, fromUid, toUid, amount, note, settlementResult::postValue);
    }

    public void deleteSettlement(String familyId, String settlementId) {
        settlementResult.setValue(new Result.Loading<>());
        settlementRepository.deleteSettlement(familyId, settlementId, result -> {
            if (result instanceof Result.Success) {
                settlementResult.postValue(new Result.Success<>(null));
            } else {
                settlementResult.postValue(new Result.Error<>(((Result.Error<?>) result).getException()));
            }
        });
    }

    public String getCurrentUserId() {
        return authRepository.getUid();
    }

    private void stopListening() {
        if (membersListener != null) {
            membersListener.remove();
            membersListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        authRepository.unregisterPreSignOutCleanup(signOutCleanup);
        stopListening();
    }
}
