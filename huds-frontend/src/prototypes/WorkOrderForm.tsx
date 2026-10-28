import React, { useState } from 'react';

interface LineItem {
    id: number;
    description: string;
    qty: number;
    unitPrice: number;
}

export const WorkOrderForm: React.FC = () => {
    const [items, setItems] = useState<LineItem[]>([
        { id: 1, description: 'Diagnostic Fee & Labor (Standard)', qty: 1, unitPrice: 95.00 },
        { id: 2, description: 'Replacement Drain Pump (OEM)', qty: 1, unitPrice: 120.00 }
    ]);

    const [customerName, setCustomerName] = useState('');
    const [phone, setPhone] = useState('');
    const [address, setAddress] = useState('');
    const [appliance, setAppliance] = useState('');

    const addItem = () => {
        setItems([...items, { id: Date.now(), description: '', qty: 1, unitPrice: 0 }]);
    };

    const removeItem = (id: number) => {
        setItems(items.filter(item => item.id !== id));
    };

    const updateItem = (id: number, field: keyof LineItem, value: string | number) => {
        setItems(items.map(item => item.id === id ? { ...item, [field]: value } : item));
    };

    const subtotal = items.reduce((sum, item) => sum + item.qty * item.unitPrice, 0);
    const hst = subtotal * 0.15; // Nova Scotia HST 15%
    const total = subtotal + hst;

    return (
        <div className="min-h-screen bg-slate-100 text-slate-800 p-4 md:p-8 font-sans">
            <div className="max-w-4xl mx-auto">

                {/* Header */}
                <div className="flex justify-between items-center mb-6">
                    <div>
                        <h1 className="text-2xl font-bold text-slate-900">🔧 Hugo's Appliance Repair</h1>
                        <p className="text-sm text-slate-500">Service Work Order & Invoice Portal (HADS v1.4)</p>
                    </div>
                    <button
                        onClick={() => alert('Work Order Draft Saved Locally!')}
                        className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 shadow"
                    >
                        Save Draft
                    </button>
                </div>

                {/* Form Box */}
                <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden space-y-6 p-6">

                    {/* Customer Details */}
                    <div>
                        <h2 className="text-lg font-semibold text-slate-800 mb-4 border-b pb-2">👤 Customer & Appliance Info</h2>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">Customer Name</label>
                                <input
                                    type="text"
                                    value={customerName}
                                    onChange={e => setCustomerName(e.target.value)}
                                    placeholder="e.g. John Smith"
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">Phone Number</label>
                                <input
                                    type="text"
                                    value={phone}
                                    onChange={e => setPhone(e.target.value)}
                                    placeholder="e.g. 902-555-0199"
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
                                />
                            </div>
                            <div className="md:col-span-2">
                                <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">Service Address (Sydney, NS)</label>
                                <input
                                    type="text"
                                    value={address}
                                    onChange={e => setAddress(e.target.value)}
                                    placeholder="e.g. 204 Bay St, Sydney, NS"
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
                                />
                            </div>
                            <div className="md:col-span-2">
                                <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">Appliance Type & Model</label>
                                <input
                                    type="text"
                                    value={appliance}
                                    onChange={e => setAppliance(e.target.value)}
                                    placeholder="e.g. Whirlpool Washer (Model: WTW4850HW)"
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
                                />
                            </div>
                        </div>
                    </div>

                    {/* Line Items */}
                    <div>
                        <div className="flex justify-between items-center mb-4 border-b pb-2">
                            <h2 className="text-lg font-semibold text-slate-800">🛠️ Service & Parts Items</h2>
                            <button onClick={addItem} className="text-sm text-blue-600 font-medium hover:underline">+ Add Line Item</button>
                        </div>

                        <div className="overflow-x-auto">
                            <table className="w-full text-left text-sm">
                                <thead>
                                <tr className="bg-slate-50 text-slate-500 border-b">
                                    <th className="py-2 px-3 font-semibold">Description</th>
                                    <th className="py-2 px-3 font-semibold w-20">Qty</th>
                                    <th className="py-2 px-3 font-semibold w-28">Unit Price ($)</th>
                                    <th className="py-2 px-3 font-semibold w-28">Total ($)</th>
                                    <th className="py-2 px-3 font-semibold w-12"></th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-200">
                                {items.map(item => (
                                    <tr key={item.id}>
                                        <td className="py-2 px-3">
                                            <input
                                                type="text"
                                                value={item.description}
                                                onChange={e => updateItem(item.id, 'description', e.target.value)}
                                                className="w-full border border-slate-200 rounded px-2 py-1 text-sm outline-none"
                                            />
                                        </td>
                                        <td className="py-2 px-3">
                                            <input
                                                type="number"
                                                value={item.qty}
                                                onChange={e => updateItem(item.id, 'qty', Number(e.target.value))}
                                                className="w-full border border-slate-200 rounded px-2 py-1 text-sm outline-none"
                                            />
                                        </td>
                                        <td className="py-2 px-3">
                                            <input
                                                type="number"
                                                value={item.unitPrice}
                                                onChange={e => updateItem(item.id, 'unitPrice', Number(e.target.value))}
                                                className="w-full border border-slate-200 rounded px-2 py-1 text-sm outline-none"
                                            />
                                        </td>
                                        <td className="py-2 px-3 font-medium">
                                            ${(item.qty * item.unitPrice).toFixed(2)}
                                        </td>
                                        <td className="py-2 px-3 text-center">
                                            <button onClick={() => removeItem(item.id)} className="text-red-500 hover:text-red-700 font-bold">×</button>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </div>

                    {/* Totals Calculation */}
                    <div className="bg-slate-50 p-4 rounded-lg flex justify-end">
                        <div className="w-72 space-y-2 text-sm">
                            <div className="flex justify-between">
                                <span className="text-slate-500">Subtotal:</span>
                                <span className="font-medium">${subtotal.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-slate-500">HST (NS 15%):</span>
                                <span className="font-medium">${hst.toFixed(2)}</span>
                            </div>
                            <div className="flex justify-between border-t border-slate-300 pt-2 text-base font-bold text-slate-900">
                                <span>Total:</span>
                                <span>${total.toFixed(2)}</span>
                            </div>
                        </div>
                    </div>

                </div>
            </div>
        </div>
    );
};