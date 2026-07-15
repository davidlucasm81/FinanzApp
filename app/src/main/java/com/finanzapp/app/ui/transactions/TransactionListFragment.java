package com.finanzapp.app.ui.transactions;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.firebase.FirestorePaths;
import com.finanzapp.app.data.model.Account;
import com.finanzapp.app.data.model.Category;
import com.finanzapp.app.data.model.Transaction;
import com.finanzapp.app.data.model.User;
import com.finanzapp.app.util.Result;
import com.finanzapp.app.viewmodel.TransactionViewModel;
import com.finanzapp.app.viewmodel.ViewModelFactory;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TransactionListFragment extends Fragment {
    private TransactionViewModel viewModel;
    private TransactionAdapter adapter;
    private String familyId;
    private final Map<String, String> categoryNames = new HashMap<>();
    private final Map<String, String> accountNames = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transaction_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        ViewModelFactory factory = new ViewModelFactory(appContainer.getAuthRepository(), appContainer.getFamilyRepository(), 
                                                        appContainer.getAccountRepository(), appContainer.getCategoryRepository(),
                                                        appContainer.getTransactionRepository());
        viewModel = new ViewModelProvider(this, factory).get(TransactionViewModel.class);

        RecyclerView rvTransactions = view.findViewById(R.id.rv_transactions);
        FloatingActionButton fabAdd = view.findViewById(R.id.fab_add_transaction);

        adapter = new TransactionAdapter(new ArrayList<>(), categoryNames, accountNames);
        rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTransactions.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {
            if (familyId != null) {
                Bundle args = new Bundle();
                args.putString("familyId", familyId);
                Navigation.findNavController(v).navigate(R.id.action_transactionListFragment_to_addEditTransactionFragment, args);
            }
        });

        resolveFamilyId();
    }

    private void resolveFamilyId() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection(FirestorePaths.USERS).document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFamilyId() != null) {
                            familyId = user.getFamilyId();
                            observeData();
                        }
                    });
        }
    }

    private void observeData() {
        viewModel.getCategories(familyId).observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                categoryNames.clear();
                for (Category c : categories) categoryNames.put(c.getId(), c.getName());
                adapter.notifyDataSetChanged();
            }
        });

        viewModel.getAccounts(familyId).observe(getViewLifecycleOwner(), accounts -> {
            if (accounts != null) {
                accountNames.clear();
                for (Account a : accounts) accountNames.put(a.getId(), a.getName());
                adapter.notifyDataSetChanged();
            }
        });

        viewModel.getTransactions(familyId).observe(getViewLifecycleOwner(), transactions -> {
            if (transactions != null) {
                adapter.updateTransactions(transactions);
            }
        });
    }

    private static class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {
        private final List<Transaction> transactions;
        private final Map<String, String> categoryNames;
        private final Map<String, String> accountNames;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "ES"));

        TransactionAdapter(List<Transaction> transactions, Map<String, String> categoryNames, Map<String, String> accountNames) {
            this.transactions = transactions;
            this.categoryNames = categoryNames;
            this.accountNames = accountNames;
        }

        void updateTransactions(List<Transaction> newTransactions) {
            this.transactions.clear();
            this.transactions.addAll(newTransactions);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction t = transactions.get(position);
            
            holder.tvDate.setText(t.getDate() != null ? dateFormat.format(t.getDate().toDate()) : "");
            holder.tvCategory.setText(categoryNames.getOrDefault(t.getCategoryId(), "Categoría"));
            holder.tvDescription.setText(t.getDescription());
            holder.tvAccount.setText(accountNames.getOrDefault(t.getAccountId(), "Cuenta"));

            boolean isIncome = "income".equals(t.getType());
            double amount = t.getAmount();
            String amountStr = (isIncome ? "+" : "-") + currencyFormat.format(amount);
            holder.tvAmount.setText(amountStr);
            holder.tvAmount.setTextColor(isIncome ? 0xFF2E7D32 : 0xFFB00020);
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate, tvCategory, tvDescription, tvAmount, tvAccount;

            ViewHolder(View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tv_date);
                tvCategory = itemView.findViewById(R.id.tv_category);
                tvDescription = itemView.findViewById(R.id.tv_description);
                tvAmount = itemView.findViewById(R.id.tv_amount);
                tvAccount = itemView.findViewById(R.id.tv_account);
            }
        }
    }
}
