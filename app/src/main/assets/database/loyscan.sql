INSERT INTO brands (id, name, color, logo) VALUES (1, 'Brand A', '#FF0000', '');
INSERT INTO brands (id, name, color, logo) VALUES (2, 'Brand B', '#00FF00', '');
INSERT INTO brands (id, name, color, logo) VALUES (3, 'Brand C', '#0000FF', '');
INSERT INTO brands (id, name, color, logo) VALUES (4, 'Brand D', '#FFFF00', '');
INSERT INTO brands (id, name, color, logo) VALUES (5, 'Brand E', '#FF00FF', '');

INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (1, 'Card A', 1, '123456789', 'EAN_13');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (2, 'Card B', 2, '987654321', 'EAN_13');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (3, 'Card C', 3, '112233445', 'EAN_13');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (4, 'SuperMart Rewards', 1, '543210987', 'CODE_128');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (5, 'Coffee Bean Club', 2, '678901234', 'QR_CODE');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (6, 'Bookworm Bonus', 3, '109876543', 'EAN_13');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (7, 'Tech Store VIP', 4, '9876543210', 'CODE_39');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (8, 'Fashion Outlet Pass', 5, '1234509876', 'AZTEC');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (9, 'Grocery Saver', 1, '1122334455', 'EAN_8');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (10, 'Pet Supplies Club', 4, '6677889900', 'DATA_MATRIX');
INSERT INTO loyalty_cards (id, name, brandId, code, barcodeFormat) VALUES (11, 'Cinema Fan Card', 2, '0011223344', 'PDF_417');
