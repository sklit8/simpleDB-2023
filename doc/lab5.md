# 6.830 Lab 5: B+ Tree Index

**Assigned: Wednesday, April 21, 2021**
**Due: Tuesday, May 4, 2021 11:59 PM EDT**

## 1. Introduction

在这个实验室中，你将实现一个B+树形索引，用于高效的查找和范围扫描。我们为你提供了实现树形结构所需的所有底层代码。你将实现搜索、拆分页面、在页面之间重新分配图元以及合并页面。

你可能会发现回顾一下教科书中的第10.3--10.7节是有帮助的，这些章节提供了关于B+树结构的详细信息，以及搜索、插入和删除的伪代码。

正如教科书所描述的和课堂上讨论的，B+树的内部节点包含多个条目，每个条目由一个键值和一个左、右子指针组成。相邻的键共享一个子指针，所以包含m个键的内部节点有m+1个子指针。叶子节点可以包含数据条目或指向其他数据库文件的数据条目的指针。为了简单起见，我们将实现一个B+树，其中叶子页实际上包含数据条目。相邻的叶子页用左右的同级指针连接在一起，所以范围扫描只需要通过根节点和内部节点进行一次初始搜索就能找到第一个叶子页。随后的叶子页是通过跟踪右（或左）同级指针找到的。

## 2. Search

看一下index/和BTreeFile.java。这是实现B+Tree的核心文件，你将在这里编写本实验的所有代码。与HeapFile不同，BTreeFile由四种不同的页面组成。正如你所期望的，树的节点有两种不同的页：内部页和叶子页。内部页在BTreeInternalPage.java中实现，而叶子页在BTreeLeafPage.java中实现。为了方便起见，我们在BTreePage.java中创建了一个抽象类，它包含了叶子页和内部页的共同代码。此外，标题页在BTreeHeaderPage.java中实现，并跟踪文件中哪些页正在使用。最后，在每个BTreeFile的开头都有一个页面，它指向树的根页和第一个标题页。这个单子页在BTreeRootPtrPage.java中实现。熟悉这些类的接口，特别是BTreePage、BTreeInternalPage和BTreeLeafPage。你将需要在你的B+Tree的实现中使用这些类。

你的第一项工作是实现BTreeFile.java中的findLeafPage()函数。这个函数用于在给定一个特定的键值的情况下找到合适的叶子页，并且用于搜索和插入。例如，假设我们有一个有两个叶子页的B+Tree（见图1）。根节点是一个内部页面，有一个条目，包含一个键（本例中为6）和两个子指针。给定一个值为1，这个函数应该返回第一个叶子页面。同样地，如果数值为8，这个函数应该返回第二个页面。不太明显的情况是，如果我们给定的键值是6，可能有重复的键，所以两个叶子页上可能都有6。在这种情况下，这个函数应该返回第一个（左边）叶子页。

[![img](https://github.com/CreatorsStack/CreatorDB/raw/master/doc/simple_tree.png)](https://github.com/CreatorsStack/CreatorDB/blob/master/doc/simple_tree.png)
													*Figure 1: A simple B+ Tree with duplicate keys*

你的`findLeafPage()`函数应该递归地搜索内部节点，直到到达与所提供的键值相对应的叶子页。为了在每一步找到合适的子页面，你应该遍历内部页面中的条目，并将条目值与提供的键值进行比较。`TreeInternalPage.iterator()`使用`TreeEntry.java`中定义的接口提供了对内部页面中条目的访问。这个迭代器允许你遍历内部页面中的键值，并访问每个键的左右子页面ID。当传入的`BTreePageId的pgcateg()`等于`BTreePageId.LEAF`时，你递归的基本情况就会发生，表明它是一个叶子页面。在这种情况下，你应该只是从缓冲池中获取该页并返回它。你不需要确认它是否真的包含所提供的键值f。

你的`findLeafPage()`代码还必须处理提供的键值f为空的情况。如果提供的值是空的，每次都在最左边的子页上递归，以便找到最左边的叶子页。找到最左边的叶子页对于扫描整个文件是很有用的。一旦找到正确的叶子页，你应该返回它。如上所述，你可以使用`BTreePageId.java`中的`pgcateg()`函数检查页面的类型。你可以假设只有叶子页和内部页会被传递给这个函数。

与其直接调用BufferPool.getPage()来获取每个内部页面和叶子页面，我们建议调用我们提供的包装函数`BTreeFile.getPage()`。它的工作原理与BufferPool.getPage()完全一样，但需要一个额外的参数来跟踪脏页的列表。这个函数在接下来的两个练习中非常重要，你将实际更新数据，因此需要跟踪脏页。

你的` findLeafPage() `实现所访问的每一个内部（非叶子）页面都应该以 READ_ONLY 权限来获取，除了返回的叶子页面，它应该以提供给函数参数的权限来获取。这些权限级别对本实验室来说并不重要，但它们对代码在未来实验室中的正常运行非常重要。

------

**Exercise 1: BTreeFile.findLeafPage()**

Implement `BTreeFile.findLeafPage()`.

After completing this exercise, you should be able to pass all the unit tests in `BTreeFileReadTest.java` and the system tests in `BTreeScanTest.java`.

------

## 3. Insert

为了使B+Tree的图元保持排序，并保持树的完整性，我们必须将图元插入到具有封闭键范围的叶子页中。如上所述，`findLeafPage()`可以用来找到我们应该插入图元的正确叶子页。然而，每个页面都有有限的槽位，我们需要能够插入图元，即使相应的叶子页面已经满了。

正如教科书中所描述的那样，试图将一个元组插入到一个满的叶子页中应该导致该页分裂，这样元组就会在两个新的页面中平均分配。每次叶子页分裂时，需要向父节点添加一个与第二页中第一个元组对应的新条目。偶尔，内部节点也可能是满的，无法接受新条目。在这种情况下，父节点应该拆分并向其父节点添加一个新条目。这可能会导致递归分裂，最终创建一个新的根节点。

在这个练习中，你将在 `BTreeFile.java `中实现 `splitLeafPage() `和 `splitInternalPage() `。如果被分割的页面是根页面，你将需要创建一个新的内部节点来成为新的根页面，并更新`BTreeRootPtrPage`。否则，你将需要获取具有READ_WRITE权限的父页，如果需要的话，递归拆分它，并添加一个新的条目。你会发现函数`getParentWithEmptySlots()`对于处理这些不同情况非常有用。在`splitLeafPage()`中，你应该将键 "复制 "到父页上，而在`splitInternalPage()`中，你应该将键 "推 "到父页上。如果你感到困惑，请参见图2，并回顾教科书中的10.5节。记住要根据需要更新新页面的父指针（为了简单起见，我们不在图中显示父指针）。当一个内部节点被分割时，你将需要更新所有被移动的子节点的父指针。你可能会发现函数`updateParentPointers()`对这项任务很有用。此外，记得更新任何被分割的叶子页面的兄弟姐妹指针。最后，返回新的元组或条目应该被插入的页面，如所提供的键字段所示。(提示：你不需要担心所提供的键实际上可能正好落在要分割的元组/条目的中心。在分割过程中，你应该忽略这个键，而只是用它来决定返回两个页面中的哪一个）。

[![img](https://github.com/CreatorsStack/CreatorDB/raw/master/doc/splitting_leaf.png)](https://github.com/CreatorsStack/CreatorDB/blob/master/doc/splitting_leaf.png)
[![img](https://github.com/CreatorsStack/CreatorDB/raw/master/doc/splitting_internal.png)](https://github.com/CreatorsStack/CreatorDB/blob/master/doc/splitting_internal.png)
																		*Figure 2: Splitting pages*

每当你创建一个新的页面，无论是因为拆分一个页面还是创建一个新的根页面，都要调用getEmptyPage()来获取新的页面。这个函数是一个抽象，它将允许我们重新使用因合并而被删除的页面（在下一节涉及）。

我们希望你使用BTreeLeafPage.iterator()和BTreeInternalPage.iterator()与叶子页和内部页进行交互，以迭代每个页面中的图元/条目。为了方便，我们还为这两种类型的页面提供了反向迭代器：BTreeLeafPage.reverseIterator（）和BTreeInternalPage.reverseIterator（）。这些反向迭代器对于将一个页面中的图元/条目子集移动到其右边的同级页面中特别有用。

如上所述，内部页面迭代器使用BTreeEntry.java中定义的接口，它有一个key和两个child pointers。它还有一个recordId，用于识别底层页面上的键和子指针的位置。我们认为一次处理一个条目是与内部页面交互的自然方式，但重要的是要记住，底层页面实际上并不存储一个条目列表，而是存储m个键和m+1个子指针的有序列表。由于BTreeEntry只是一个接口，而不是实际存储在页面上的对象，更新BTreeEntry的字段不会修改底层页面。为了改变页面上的数据，你需要调用BTreeInternalPage.updateEntry（）。此外，删除一个条目实际上只删除了一个键和一个子指针，所以我们提供了BTreeInternalPage.deleteKeyAndLeftChild()和BTreeInternalPage.deleteKeyAndRightChild()的功能来明确这一点。该条目的recordId被用来寻找要删除的key和child指针。插入一个条目也只插入一个键和单个子指针（除非它是第一个条目），所以BTreeInternalPage.insertEntry()检查所提供的条目中的一个子指针是否与页面上现有的一个子指针重叠，在该位置插入条目将保持键的排序顺序。

在 splitLeafPage() 和 splitInternalPage() 中，你需要用任何新创建的页面以及由于新指针或新数据而修改的页面来更新 dirtypages 的集合。这就是BTreeFile.getPage()的用武之地。每次你获取一个页面时，BTreeFile.getPage()都会检查该页面是否已经存储在本地缓存（dirtypages）中，如果它在那里找不到所请求的页面，就会从缓冲池中获取它。BTreeFile.getPage()还将页面添加到dirtypages缓存中，如果它们是以读写权限获取的，因为据推测它们很快就会被弄脏。这种方法的一个优点是，如果在一次元组插入或删除过程中多次访问相同的页面，它可以防止更新的损失。

请注意，与HeapFile.insertTuple()大相径庭的是，BTreeFile.insertTuple()可能会返回一大组脏页，尤其是在任何内部页面被分割的情况下。你可能还记得以前的实验，返回脏页集是为了防止缓冲池在脏页被刷新之前驱逐它们。

------

**Warning**：由于B+Tree是一个复杂的数据结构，在修改它之前，了解每个合法B+Tree的必要属性是很有帮助的。这里是一个非正式的列表：

1. 如果一个父节点指向一个子节点，子节点必须指向那些相同的父节点。
2. 如果一个叶子节点指向一个右边的兄弟姐妹，那么右边的兄弟姐妹就会指向该叶子节点的左边的兄弟姐妹。
3. 第一个和最后一个叶子必须分别指向空的左和右兄弟姐妹。
4. 记录的Id必须与它们实际所在的页面相匹配。
5. 有非叶子的节点中的键必须大于左子的任何键，并且小于右子的任何键。
6. 有叶子的节点中的键必须大于或等于左边子节点的任何键，并且小于或等于右边子节点的任何键。
7. 一个节点要么有所有非叶子的孩子，要么有所有叶子的孩子。
8. 一个非根节点不能少于一半。

我们已经在BTreeChecker.java文件中实现了对所有这些属性的机械化检查。在BTreeFileDeleteTest.java中，这个方法也被用来测试你的B+Tree实现。请随意添加对该函数的调用，以帮助调试你的实现，就像我们在BTreeFileDeleteTest.java中做的那样。

N.B.

1. 检查器方法应该总是在树的初始化之后，在开始和完成对键插入或删除的完整调用之前传递，但不一定在内部方法中传递。

2. 一棵树可能形成得很好（因此通过了checkRep()），但仍然不正确。例如，空的树将总是通过checkRep()，但不一定总是正确的（如果你刚刚插入了一个元组，树不应该是空的）。***

**Exercise 2: Splitting Pages**

Implement `BTreeFile.splitLeafPage()` and `BTreeFile.splitInternalPage()`.

完成这个练习后，你应该能够通过BTreeFileInsertTest.java中的单元测试。你也应该能够通过 BTreeFileInsertTest.java 中的系统测试。一些系统测试案例可能需要几秒钟的时间来完成。这些文件将测试你的代码是否正确地插入图元和分割页面，并处理重复的图元。

------

## 4. Delete

为了保持树的平衡，不浪费不必要的空间，B+树中的删除可能会导致页面重新分配图元（图3），或者最终合并（见图4）。你可能会发现复习一下教科书中的第10.6节是很有用的。

[![img](https://github.com/CreatorsStack/CreatorDB/raw/master/doc/redist_leaf.png)](https://github.com/CreatorsStack/CreatorDB/blob/master/doc/redist_leaf.png)
[![img](https://github.com/CreatorsStack/CreatorDB/raw/master/doc/redist_internal.png)](https://github.com/CreatorsStack/CreatorDB/blob/master/doc/redist_internal.png)
*Figure 3: Redistributing pages*

[![img](https://github.com/CreatorsStack/CreatorDB/raw/master/doc/merging_leaf.png)](https://github.com/CreatorsStack/CreatorDB/blob/master/doc/merging_leaf.png)
[![img](https://github.com/CreatorsStack/CreatorDB/raw/master/doc/merging_internal.png)](https://github.com/CreatorsStack/CreatorDB/blob/master/doc/merging_internal.png)
*Figure 4: Merging pages*

正如教科书中所描述的那样，如果试图从一个不满一半的叶子页中删除一个图元，应该导致该页从它的一个兄弟姐妹那里偷取图元或与它的一个兄弟姐妹合并。如果该页的一个兄弟姐妹有多余的图元，这些图元应该在两个页面之间平均分配，并且父页的条目应该相应地被更新（见图3）。然而，如果兄弟姐妹也处于最小的占用率，那么这两个页面就应该合并，并从父页面删除条目（图4）。反过来，从父本中删除一个条目可能会导致父本的占用率低于一半。在这种情况下，父本应该从其兄弟姐妹那里偷取条目，或者与兄弟姐妹合并。如果从根节点上删除最后一个条目，这可能导致递归合并，甚至删除根节点。

在这个练习中，你将在`BTreeFile.java`中实现 `stealFromLeafPage()`, `stealFromLeftInternalPage()`, `stealFromRightInternalPage()`, `mergeLeafPages() `和 `mergeInternalPages()`。在前三个函数中，你将实现代码，在兄弟姐妹有图元/条目的情况下均匀地重新分配图元/条目。记住要更新父代中相应的键字段（仔细看看图3中是如何做到的--键字在父代中被有效地 "旋转 "了）。在`stealFromLeftInternalPage()`/`stealFromRightInternalPage()`中，你还需要更新被移动的子节点的父节点指针。你应该可以为这个目的重新使用函数`updateParentPointers()`。

在`mergeLeafPages()`和`mergeInternalPages()`中，你将实现合并页面的代码，有效地执行`splitLeafPage()`和`splitInternalPage()`的逆过程。你会发现函数`deleteParentEntry()`对于处理所有不同的递归情况非常有用。一定要在被删除的页面上调用`setEmptyPage()`，以使它们可以被重新使用。与之前的练习一样，我们建议使用`BTreeFile.getPage()`来封装获取页面的过程，并保持最新的脏页面列表。

------

**Exercise 3: Redistributing pages**

Implement `BTreeFile.stealFromLeafPage()`, `BTreeFile.stealFromLeftInternalPage()`, `BTreeFile.stealFromRightInternalPage()`.

After completing this exercise, you should be able to pass some of the unit tests in `BTreeFileDeleteTest.java` (such as `testStealFromLeftLeafPage` and `testStealFromRightLeafPage`). The system tests may take several seconds to complete since they create a large B+ tree in order to fully test the system.

**Exercise 4: Merging pages**

Implement `BTreeFile.mergeLeafPages()` and `BTreeFile.mergeInternalPages()`.

Now you should be able to pass all unit tests in `BTreeFileDeleteTest.java` and the system tests in `systemtest/BTreeFileDeleteTest.java`.

------

## 5. Transactions

你可能还记得，B+树可以通过使用下一个键锁定来防止幻影图元在两个连续的范围扫描之间出现。由于SimpleDB使用了页级的、严格的两阶段锁定，如果B+树被正确实现，对幻影的保护实际上是免费的。因此，在这一点上，你也应该能够通过BTreeNextKeyLockingTest。

此外，如果你在B+树代码中正确实现了锁定，你应该能够通过test/simpledb/BTreeDeadlockTest.java的测试。

如果一切实现正确，你也应该能够通过BTreeTest的系统测试。我们预计很多人会觉得BTreeTest很难，所以它不是必须的，但我们会给能成功运行它的人加分。请注意，这个测试可能需要一分钟的时间来完成。