package com.finanzapp.app.ui.monetization;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.finanzapp.app.FinanzAppApplication;
import com.finanzapp.app.R;
import com.finanzapp.app.data.monetization.BillingRepository;
import com.finanzapp.app.databinding.FragmentPaywallBinding;
import com.revenuecat.purchases.Package;
import com.revenuecat.purchases.Purchases;
import com.revenuecat.purchases.interfaces.PurchaseCallback;
import com.revenuecat.purchases.models.StoreTransaction;

import java.util.List;

import com.finanzapp.app.util.Result;

public class PaywallFragment extends Fragment {
    private FragmentPaywallBinding binding;
    private BillingRepository billingRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPaywallBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FinanzAppApplication.AppContainer appContainer = ((FinanzAppApplication) requireActivity().getApplication()).getAppContainer();
        billingRepository = appContainer.getBillingRepository();

        setupToolbar();
        setupOfferings();
        setupObservers();

        binding.btnRestore.setOnClickListener(v -> {
            binding.pbLoading.setVisibility(View.VISIBLE);
            billingRepository.restorePurchases();
        });
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
    }

    private void setupOfferings() {
        android.util.Log.d("PaywallFragment", "Fetching offerings...");
        Purchases.getSharedInstance().getOfferings(new com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback() {
            @Override
            public void onReceived(@NonNull com.revenuecat.purchases.Offerings offerings) {
                if (isAdded()) {
                    binding.pbLoading.setVisibility(View.GONE);
                    if (offerings.getCurrent() != null) {
                        List<Package> availablePackages = offerings.getCurrent().getAvailablePackages();
                        android.util.Log.d("PaywallFragment", "Offerings received. Packages: " + availablePackages.size());
                        if (!availablePackages.isEmpty()) {
                            Package p = availablePackages.get(0);
                            String price = p.getProduct().getPrice().getFormatted();
                            binding.btnSubscribe.setText(getString(R.string.premium_subscribe_button, price));
                            binding.btnSubscribe.setOnClickListener(v -> {
                                android.util.Log.d("PaywallFragment", "Buy button clicked for package: " + p.getIdentifier());
                                purchasePackage(p);
                            });
                        } else {
                            android.util.Log.w("PaywallFragment", "Offerings current found but availablePackages is empty.");
                            binding.btnSubscribe.setOnClickListener(v -> {
                                Toast.makeText(requireContext(), "No hay productos disponibles para comprar actualmente.", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        android.util.Log.w("PaywallFragment", "Offerings received but current is null.");
                        binding.btnSubscribe.setOnClickListener(v -> {
                            Toast.makeText(requireContext(), "Error de configuración: No hay ofertas activas.", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }

            @Override
            public void onError(@NonNull com.revenuecat.purchases.PurchasesError purchasesError) {
                if (isAdded()) {
                    android.util.Log.e("PaywallFragment", "Error fetching offerings: " + purchasesError.getMessage());
                    binding.pbLoading.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), purchasesError.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.btnSubscribe.setOnClickListener(v -> {
                        Toast.makeText(requireContext(), "No se pudieron cargar las ofertas: " + purchasesError.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void purchasePackage(Package packageToPurchase) {
        binding.pbLoading.setVisibility(View.VISIBLE);
        Purchases.getSharedInstance().purchase(
            new com.revenuecat.purchases.PurchaseParams.Builder(requireActivity(), packageToPurchase).build(),
            new PurchaseCallback() {
                @Override
                public void onCompleted(@NonNull StoreTransaction storeTransaction, @NonNull com.revenuecat.purchases.CustomerInfo customerInfo) {
                    if (isAdded()) {
                        binding.pbLoading.setVisibility(View.GONE);
                        if (!customerInfo.getEntitlements().getActive().isEmpty()) {
                            Toast.makeText(requireContext(), R.string.premium_success, Toast.LENGTH_LONG).show();
                            NavHostFragment.findNavController(PaywallFragment.this).navigateUp();
                        }
                    }
                }

                @Override
                public void onError(@NonNull com.revenuecat.purchases.PurchasesError purchasesError, boolean b) {
                    if (isAdded()) {
                        binding.pbLoading.setVisibility(View.GONE);
                        if (purchasesError.getCode() != com.revenuecat.purchases.PurchasesErrorCode.PurchaseCancelledError) {
                            Toast.makeText(requireContext(), purchasesError.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        );
    }

    private void setupObservers() {
        billingRepository.getPremiumType().observe(getViewLifecycleOwner(), type -> {
            if (type == BillingRepository.PremiumType.UNKNOWN) return;

            if (type != BillingRepository.PremiumType.NONE && isAdded()) {
                binding.pbLoading.setVisibility(View.GONE);
                // If they are already premium, they shouldn't be here, or they just restored
                if (binding.btnRestore.getVisibility() == View.VISIBLE) {
                     NavHostFragment.findNavController(this).navigateUp();
                }
            }
        });

        billingRepository.getRestoreResultEvent().observe(getViewLifecycleOwner(), result -> {
            if (isAdded()) {
                binding.pbLoading.setVisibility(View.GONE);
                if (result instanceof Result.Success) {
                    boolean found = ((Result.Success<Boolean>) result).getData();
                    if (found) {
                        Toast.makeText(requireContext(), R.string.premium_success, Toast.LENGTH_LONG).show();
                        NavHostFragment.findNavController(this).navigateUp();
                    } else {
                        Toast.makeText(requireContext(), R.string.premium_no_purchase_found, Toast.LENGTH_LONG).show();
                    }
                } else if (result instanceof Result.Error) {
                    Exception e = ((Result.Error<?>) result).getException();
                    String msg = e != null ? e.getMessage() : getString(R.string.error_generic);
                    Toast.makeText(requireContext(), getString(R.string.error_with_message, msg), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
