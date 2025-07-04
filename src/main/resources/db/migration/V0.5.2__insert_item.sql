
# 데이터 타입 추가
INSERT INTO item_type(id, name)
VALUES (1, 'HAIR'),
       (2, 'CLOTH'),
       (3, 'PANT');


ALTER TABLE item ADD COLUMN unity_file_path VARCHAR(255) NOT NULL AFTER file_path;


# 헤어 데이터 추가
INSERT INTO item(item_type_id, name, file_path, unity_file_path, point)
VALUES (1, 'New_Hair_01', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_02', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_03', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_04', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_05', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_06', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_07', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_08', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_09', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_10', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_11', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_12', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_13', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_14', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_16', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_17', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_18', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_19', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'New_Hair_20', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'Normal_Hair1', 'items/Hair/', 'Assets/05.Resource/Hair/', 100),
       (1, 'Normal_Hair2', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair3', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair4', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair5', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair6', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair7', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair8', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair9', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair10', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair11', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair12', 'items/Hair/', 'Assets/05.Resource/Hair/', 50),
       (1, 'Normal_Hair13', 'items/Hair/', 'Assets/05.Resource/Hair/', 50);


# 의상 데이터 추가
INSERT INTO item(item_type_id, name, file_path, unity_file_path, point)
VALUES (2, 'New_Cloth_1', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_2', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_3', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_4', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_5', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_6', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_7', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_8', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_9', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_10', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_11', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'New_Cloth_12', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 100),
       (2, 'Normal_Cloth_1', 'items/Cloth/', 'Assets/05.Resource/Cloth/', 50);



# 바지 데이터 추가
INSERT INTO item(item_type_id, name, file_path, unity_file_path, point)
VALUES (3, 'New_Pant_01', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_02', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_03', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_04', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_05', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_06', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_07', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_08', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_09', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_10', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_11', 'items/Pant/', 'Assets/05.Resource/Pant/', 40),
       (3, 'New_Pant_12', 'items/Pant/', 'Assets/05.Resource/Pant/', 40);
