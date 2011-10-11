package scala.tools.refactoring
package tests.implementations

import implementations.MergeParameterLists
import tests.util.TestHelper
import tests.util.TestRefactoring

class MergeParameterListsTest extends TestHelper with TestRefactoring {

  outer =>
    
  import outer.global._
  
  def mergeParameterLists(mergePositions: List[Int])(pro: FileSet) = new TestRefactoringImpl(pro) {
    val refactoring = new MergeParameterLists with SilentTracing with GlobalIndexes {
      val global = outer.global
      val cuIndexes = pro.trees map (_.pos.source.file) map (file => global.unitOfFile(file).body) map CompilationUnitIndex.apply
      val index = GlobalIndex(cuIndexes)
    }
    val changes = performRefactoring(mergePositions)
  }.changes
  
  
  @Test(expected=classOf[RefactoringException])
  def tooSmallMergePosition = new FileSet {
    """
    package mergeParameterLists.tooSmallMergePosition
    class A {
      def /*(*/foo/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
    }
    """ becomes
    """
    package mergeParameterLists.tooSmallMergePosition
    class A {
      def /*(*/foo/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
    }
    """
  } applyRefactoring(mergeParameterLists(0::1::Nil))
  
  @Test(expected=classOf[RefactoringException])
  def tooBigMergePosition = new FileSet {
    """
    package mergeParameterLists.tooBigMergePosition
    class A {
      def /*(*/foo/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
    }
    """ becomes
    """
    package mergeParameterLists.tooBigMergePosition
    class A {
      def /*(*/foo/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
    }
    """
  } applyRefactoring(mergeParameterLists(1::3::Nil))
  
  @Test(expected=classOf[RefactoringException])
  def unsortedMergePositions = new FileSet {
    """
    package mergeParameterLists.unsortedMergePositions
    class A {
      def /*(*/foo/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
    }
    """ becomes
    """
    package mergeParameterLists.unsortedMergePositions
    class A {
      def /*(*/foo/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
    }
    """
  } applyRefactoring(mergeParameterLists(2::1::Nil))
  
  @Test(expected=classOf[RefactoringException])
  def repeatedMergePosition = new FileSet {
    """
    package mergeParameterLists.repeatedMergePosition
    class A {
      def /*(*/foo/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
    }
    """ becomes
    """
    package mergeParameterLists.repeatedMergePosition
    class A {
      def /*(*/foo/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
    }
    """
  } applyRefactoring(mergeParameterLists(1::1::2::Nil))
  
  
  @Test
  def mergeAllLists = new FileSet {
    """
    package mergeParameterLists.mergeAllLists
    class A {
      def /*(*/toMerge/*)*/(a: Int)(b: Int)(c: Int, d: Int)(e: Int) = a + b
    }
    """ becomes
    """
    package mergeParameterLists.mergeAllLists
    class A {
      def /*(*/toMerge/*)*/(a: Int, b: Int, c: Int, d: Int, e: Int) = a + b
    }
    """
  } applyRefactoring(mergeParameterLists(1::2::3::Nil))
  
  @Test
  def mergeSomeLists = new FileSet {
    """
    package mergeParameterLists.mergeSomeLists
    class A {
      def /*(*/toMerge/*)*/(a: Int)(b: Int)(c: Int, d: Int)(e: Int) = a + b
    }
    """ becomes
    """
    package mergeParameterLists.mergeSomeLists
    class A {
      def /*(*/toMerge/*)*/(a: Int, b: Int)(c: Int, d: Int, e: Int) = a + b
    }
    """
  } applyRefactoring(mergeParameterLists(1::3::Nil))
  
  @Test
  def mergeWithCall = new FileSet {
    """
    package mergeParameterLists.mergeWithCall
    class A {
      def /*(*/toMerge/*)*/(a: Int)(b: Int)(c: Int, d: Int)(e: Int) = a + b
      val x = toMerge(1)(2)(3, 4)(5)
    }
    """ becomes
    """
    package mergeParameterLists.mergeWithCall
    class A {
      def /*(*/toMerge/*)*/(a: Int, b: Int)(c: Int, d: Int, e: Int) = a + b
      val x = toMerge(1, 2)(3, 4, 5)
    }
    """
  } applyRefactoring(mergeParameterLists(1::3::Nil))
  
  @Test
  def mergeMethodSubclass = new FileSet {
    """
    package mergeParameterLists.mergeMethodSubclass
    class Parent {
      def /*(*/method/*)*/(first: Int)(second: Int)(a: Int, b: Int)(c: Int) = first + second
    }
    class Child extends Parent {
      override def method(first:Int)(second: Int)(a: Int, b: Int)(c: Int) = a + b + c
    }
    """ becomes
    """
    package mergeParameterLists.mergeMethodSubclass
    class Parent {
      def /*(*/method/*)*/(first: Int, second: Int)(a: Int, b: Int, c: Int) = first + second
    }
    class Child extends Parent {
      override def method(first:Int, second: Int)(a: Int, b: Int, c: Int) = a + b + c
    }
    """ 
  } applyRefactoring(mergeParameterLists(1::3::Nil))
  
  @Test
  def mergeMethodSuperclass = new FileSet {
    """
    package mergeParameterLists.mergeMethodSuperclass
    class Parent {
      def method(first: Int)(second: Int)(a: Int, b: Int)(c: Int) = first + second
    }
    class Child extends Parent {
      override def /*(*/method/*)*/(first:Int)(second: Int)(a: Int, b: Int)(c: Int) = a + b + c
    }
    """ becomes
    """
    package mergeParameterLists.mergeMethodSuperclass
    class Parent {
      def method(first: Int, second: Int)(a: Int, b: Int, c: Int) = first + second
    }
    class Child extends Parent {
      override def /*(*/method/*)*/(first:Int, second: Int)(a: Int, b: Int, c: Int) = a + b + c
    }
    """ 
  } applyRefactoring(mergeParameterLists(1::3::Nil))
  
  @Test
  def mergeMethodAliased = new FileSet {
    """
    package mergeParameterLists.mergeMethodAliased
    class A {
      def /*(*/method/*)*/(a: Int)(b: Int)(c: Int)(d: Int) = a + b + c + d
      def alias = method _
      val ten = method(1)(2)(3)(4)
    }
    """ becomes
    """
    package mergeParameterLists.mergeMethodAliased
    class A {
      def /*(*/method/*)*/(a: Int, b: Int, c: Int)(d: Int) = a + b + c + d
      def alias = method _
      val ten = method(1, 2, 3)(4)
    }
    """
  } applyRefactoring(mergeParameterLists(1::2::Nil))
  
  @Test
  def mergePartiallyApplied= new FileSet {
    """
    package mergeParameterLists.mergePartiallyApplied
    class A {
      def /*(*/method/*)*/(a: Int)(b: Int)(c: Int)(d: Int) = a + b + c + d
      def partial = method(1)(2) _
      val ten = partial(3)(4)
    }
    """ becomes
    """
    package mergeParameterLists.mergePartiallyApplied
    class A {
      def /*(*/method/*)*/(a: Int, b: Int)(c: Int, d: Int) = a + b + c + d
      def partial = method(1, 2) _
      val ten = partial(3, 4)
    }
    """
  } applyRefactoring(mergeParameterLists(1::3::Nil))
  
  @Test
  def repeatedlyPartiallyApplied = new FileSet {
    """
    package mergeParameterLists.repeatedlyPartiallyApplied
    class A {
      def /*(*/add/*)*/(a: Int)(b: Int)(c: Int, d: Int)(e: Int)(f: Int)(g: Int, h: Int)(i: Int)(j: Int) = a + b + c + d + e
      def firstPartial = add(1)(2) _
      def secondPartial = firstPartial(3, 4)(5)
      def thirdPartial = secondPartial(6)(7, 8)
      val result = thirdPartial(9)(10)
    }
    """  becomes
    """
    package mergeParameterLists.repeatedlyPartiallyApplied
    class A {
      def /*(*/add/*)*/(a: Int, b: Int)(c: Int, d: Int, e: Int)(f: Int, g: Int, h: Int)(i: Int, j: Int) = a + b + c + d + e
      def firstPartial = add(1, 2) _
      def secondPartial = firstPartial(3, 4, 5)
      def thirdPartial = secondPartial(6, 7, 8)
      val result = thirdPartial(9, 10)
    }
    """
  } applyRefactoring(mergeParameterLists(1::3::5::7::Nil))
  
  @Test
  def aliasToVal = new FileSet {
    """
      package mergeParameterLists.aliasToVal
      class A {
        def /*(*/add/*)*/(a: Int)(b: Int)(c: Int, d: Int)(e: Int) = a + b + c + d + e
        val alias = add _
        val result = alias(1)(2)/*FIXME*/(3, 4)(5)
      }
    """  becomes
    """
      package mergeParameterLists.aliasToVal
      class A {
        def /*(*/add/*)*/(a: Int, b: Int)(c: Int, d: Int, e: Int) = a + b + c + d + e
        val alias = add _
        val result = alias(1, 2)apply/*FIXME*/(3, 4, 5)
      }
    """
  } applyRefactoring(mergeParameterLists(1::3::Nil))
  
  @Test
  def repeatedlyPartiallyAppliedVal = new FileSet {
    """
      package mergeParameterLists.repeatedlyPartiallyAppliedVal
      class A {
        def /*(*/add/*)*/(a: Int)(b: Int)(c: Int, d: Int)(e: Int)(f: Int)(g: Int) = a + b + c + d + e + f + g
        val firstPartial = add(1)(2) _
        val secondPartial = firstPartial(3, 4)(5)
        val result = secondPartial(6)(7)
      }
    """ becomes
    """
      package mergeParameterLists.repeatedlyPartiallyAppliedVal
      class A {
        def /*(*/add/*)*/(a: Int, b: Int)(c: Int, d: Int, e: Int)(f: Int, g: Int) = a + b + c + d + e + f + g
        val firstPartial = add(1, 2) _
        val secondPartial = firstPartial(3, 4, 5)
        val result = secondPartial(6, 7)
      }
    """
  } applyRefactoring(mergeParameterLists(1::3::5::Nil))
  
  @Test(expected=classOf[RefactoringException])
  def mergePointUsedForCurrying = new FileSet {
    """
    package mergeParameterLists.mergePointUsedForCurrying
    class A {
      def /*(*/method/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
      val curried = method(1)(2) _
    }
    """ becomes
    """
    package mergeParameterLists.mergePointUsedForCurrying
    class A {
      def /*(*/method/*)*/(a: Int)(b: Int)(c: Int) = a + b + c
      val curried = method(1)(2) _
    }
    """
  } applyRefactoring(mergeParameterLists(1::2::Nil))
  
}